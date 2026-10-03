<?php
// Одноразовый: сжать и уменьшить скриншоты для лендинга (GD)
$pairs = [
    ['/tmp/scr_01_main.png', '/www/wwwroot/kladovka.dr6ter.ru/app/screens/01-main.png'],
    ['/tmp/scr_02_sync.png', '/www/wwwroot/kladovka.dr6ter.ru/app/screens/02-sync.png'],
    ['/tmp/scr_03_item_edit.png', '/www/wwwroot/kladovka.dr6ter.ru/app/screens/03-item-edit.png'],
    ['/tmp/scr_04_place_edit.png', '/www/wwwroot/kladovka.dr6ter.ru/app/screens/04-place-edit.png'],
];
foreach ($pairs as [$src, $dst]) {
    $im = @imagecreatefrompng($src);
    if (!$im) { echo "FAIL $src\n"; continue; }
    $w = imagesx($im); $h = imagesy($im);
    $nw = 540; $nh = (int)round($h * $nw / $w);
    $out = imagecreatetruecolor($nw, $nh);
    imagecopyresampled($out, $im, 0, 0, 0, 0, $nw, $nh, $w, $h);
    imagepng($out, $dst, 9);
    echo basename($src) . ' -> ' . filesize($dst) . " bytes ($nw x $nh)\n";
    imagedestroy($im); imagedestroy($out);
}