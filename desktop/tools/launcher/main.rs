//! Нативный лаунчер «Кладовки» для Windows.
//!
//! К exe приклеен payload: jlink-рантайм + uber-jar приложения. При первом запуске
//! распаковывает их в `%LOCALAPPDATA%\Kladovka\r-<хэш>` и стартует java.exe
//! (не javaw: окно консоли всё равно гасится флагом CREATE_NO_WINDOW ниже, а
//! stderr с падениями пишется в last-run.log — при падении он не пропадает).
//! Повторные запуски распаковку пропускают, так что старт быстрый.
//!
//! Собирается rustc без единой внешней зависимости; WinAPI вызывается напрямую
//! через extern "system", поэтому cargo и crates.io не нужны.
//!
//! Раскладка exe (создаётся tools/SingleExe.java):
//!   [заглушка exe] [данные файлов] [индекс] [u64 LE — смещение индекса]
//! Индекс: u32 LE число файлов, затем на каждый u32 LE длина пути, путь в UTF-8,
//! u64 LE смещение и u64 LE длина внутри файла.

use std::env;
use std::fs;
use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

/// Задаётся на этапе сборки (`KLADOVKA_PAYLOAD_HASH` в окружении rustc).
/// Меняется вместе с содержимым — значит, новая сборка распакуется заново.
const PAYLOAD_HASH: &str = env!("KLADOVKA_PAYLOAD_HASH");

const APP_DIR_NAME: &str = "Kladovka";
const MAIN_CLASS: &str = "ru.kladovka.MainKt";
const JAR_NAME: &str = "kladovka.jar";
const COMPLETE_MARKER: &str = ".complete";

#[cfg(windows)]
#[link(name = "user32")]
extern "system" {
    fn MessageBoxW(
        hwnd: *mut core::ffi::c_void,
        text: *const u16,
        caption: *const u16,
        utype: u32,
    ) -> i32;
}

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

/// Показать ошибку пользователю. У лаунчера нет консоли, поэтому единственный
/// способ сообщить о сбое — диалог.
fn alert(msg: &str) {
    #[cfg(windows)]
    unsafe {
        let text = wide(msg);
        let caption = wide("Кладовка — не удалось запустить");
        const MB_ICONERROR: u32 = 0x10;
        MessageBoxW(std::ptr::null_mut(), text.as_ptr(), caption.as_ptr(), MB_ICONERROR);
    }
    #[cfg(not(windows))]
    eprintln!("{msg}");
}

struct Entry {
    path: String,
    offset: u64,
    len: u64,
}

fn read_index(exe: &Path) -> std::io::Result<Vec<Entry>> {
    let mut f = File::open(exe)?;
    let size = f.metadata()?.len();
    if size < 16 {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "файл слишком мал — payload не приклеен",
        ));
    }

    // Последние 8 байт — смещение индекса.
    f.seek(SeekFrom::End(-8))?;
    let mut tail = [0u8; 8];
    f.read_exact(&mut tail)?;
    let index_off = u64::from_le_bytes(tail);
    if index_off + 8 > size {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "смещение индекса за пределами файла",
        ));
    }

    f.seek(SeekFrom::Start(index_off))?;
    let mut count_raw = [0u8; 4];
    f.read_exact(&mut count_raw)?;
    let count = u32::from_le_bytes(count_raw) as usize;

    let mut entries = Vec::with_capacity(count);
    for _ in 0..count {
        // Путь у каждой записи свой: рантайм и jar лежат по разным адресам.
        let mut path_len_raw = [0u8; 4];
        f.read_exact(&mut path_len_raw)?;
        let path_len = u32::from_le_bytes(path_len_raw) as usize;
        if path_len > 4096 {
            return Err(std::io::Error::new(
                std::io::ErrorKind::InvalidData,
                "неправдоподобная длина пути в индексе",
            ));
        }
        let mut path_bytes = vec![0u8; path_len];
        f.read_exact(&mut path_bytes)?;

        let mut off = [0u8; 8];
        let mut len = [0u8; 8];
        f.read_exact(&mut off)?;
        f.read_exact(&mut len)?;

        entries.push(Entry {
            path: String::from_utf8(path_bytes)
                .map_err(|_| std::io::Error::new(std::io::ErrorKind::InvalidData, "путь не UTF-8"))?,
            offset: u64::from_le_bytes(off),
            len: u64::from_le_bytes(len),
        });
    }
    if entries.is_empty() {
        return Err(std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "payload пуст",
        ));
    }
    Ok(entries)
}

fn extract(exe: &Path, dest: &Path) -> std::io::Result<()> {
    let entries = read_index(exe)?;
    let mut src = File::open(exe)?;

    // Пишем во временный каталог и переименовываем только после успеха: если
    // распаковка оборвётся на середине, следующий запуск не увидит половину файлов.
    let tmp = dest.with_extension("partial");
    let _ = fs::remove_dir_all(&tmp);
    fs::create_dir_all(&tmp)?;

    for e in &entries {
        let target = tmp.join(&e.path);
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent)?;
        }
        src.seek(SeekFrom::Start(e.offset))?;
        let mut buf = vec![0u8; e.len as usize];
        src.read_exact(&mut buf)?;
        fs::write(&target, &buf)?;
    }

    fs::write(tmp.join(COMPLETE_MARKER), PAYLOAD_HASH)?;

    let _ = fs::remove_dir_all(dest);
    fs::rename(&tmp, dest)?;
    Ok(())
}

/// Каждая сборка распаковывается в свой каталог, а они по 130 МБ каждый.
/// Старые убираем, иначе со временем они съедят несколько гигабайт.
fn remove_stale(base: &Path, keep: &Path) {
    let Ok(entries) = fs::read_dir(base) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if !path.is_dir() || !name.starts_with("r-") || path == keep {
            continue;
        }
        // Не мешает старой копии, если она сейчас запущена, — просто пропускаем.
        let _ = fs::remove_dir_all(&path);
    }
}

fn app_root() -> Option<PathBuf> {
    // LOCALAPPDATA есть у любого залогиненного пользователя Windows; это правильное
    // место для распакованного рантайма — Program Files писать нельзя.
    if let Ok(p) = env::var("LOCALAPPDATA") {
        if !p.is_empty() {
            return Some(PathBuf::from(p));
        }
    }
    env::var_os("USERPROFILE").map(PathBuf::from)
}

fn main() {
    let exe = match env::current_exe() {
        Ok(p) => p,
        Err(e) => {
            alert(&format!("Не удалось определить путь к программе: {e}"));
            return;
        }
    };

    let base = match app_root() {
        Some(p) => p.join(APP_DIR_NAME),
        None => {
            alert("Не найден каталог пользователя (LOCALAPPDATA).\nУстановите программу обычным образом и запустите снова.");
            return;
        }
    };
    let dest = base.join(format!("r-{PAYLOAD_HASH}"));

    // Маркер есть — значит, прошлая распаковка завершилась полностью.
    if !dest.join(COMPLETE_MARKER).exists() {
        if let Err(e) = extract(&exe, &dest) {
            alert(&format!(
                "Не удалось подготовить программу к запуску.\n\n{e}\n\nПроверьте, что папка\n{}\nдоступна для записи.", base.display()
            ));
            return;
        }
    }
    remove_stale(&base, &dest);

    let java = dest.join("bin").join("java.exe");
    let jar = dest.join("app").join(JAR_NAME);
    if !java.exists() || !jar.exists() {
        alert(&format!(
            "Файлы программы повреждены или удалены.\n\nОжидалось:\n{}\n{}\n\nУдалите папку {} и запустите программу снова.",
            java.display(), jar.display(), dest.display()
        ));
        return;
    }

    let log_path = base.join("last-run.log");
    let log = match File::create(&log_path) {
        Ok(f) => Stdio::from(f),
        Err(_) => Stdio::null(),
    };

    let mut cmd = Command::new(&java);
    cmd.arg("-XX:TieredStopAtLevel=1")
        .arg("-XX:+UseSerialGC")
        .arg("-Xss4m")
        .arg("-Dfile.encoding=UTF-8")
        .arg("-Dsun.jnu.encoding=UTF-8")
        .arg("-Djava.awt.headless=false")
        .arg("-cp")
        .arg(&jar)
        .arg(MAIN_CLASS)
        .current_dir(&dest)
        .stdout(Stdio::null())
        .stderr(log);

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // Окна консоли не создаём: приложение запускается двойным щелчком в проводнике.
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    match cmd.spawn() {
        // Лаунчер сразу выходит: окно приложения живёт своей жизнью, а ошибки JVM
        // пишутся в last-run.log.
        Ok(_) => {}
        Err(e) => alert(&format!("Не удалось запустить программу: {e}")),
    }
}
