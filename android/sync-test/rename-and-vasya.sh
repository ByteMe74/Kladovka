#!/bin/bash
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
// 1) переименование контейнеров: Контейнер 1 -> К01 ...
$rows=$db->query("SELECT id,name FROM containers ORDER BY id")->fetchAll(PDO::FETCH_ASSOC);
foreach($rows as $i=>$r){
    if(preg_match("/^\D*(\d+)$/", $r["name"], $m)){
        $new="К".str_pad((int)$m[1],2,"0",STR_PAD_LEFT);
        if($new!==$r["name"]){
            $db->prepare("UPDATE containers SET name=? WHERE id=?")->execute([$new,$r["id"]]);
            echo "container {$r["id"]}: {$r["name"]} -> $new\n";
        }
    }
}
// 2) учётка vasya (email пустой - подтвердит при первом входе через форму)
$stmt=$db->prepare("SELECT id FROM users WHERE username=?");
$stmt->execute(["vasya"]);
if($stmt->fetchColumn()){
    echo "vasya: already exists\n";
}else{
    $pw="Vasya!2026";
    $db->prepare("INSERT INTO users (username,email,password_hash,email_verified,created_at,ip) VALUES (?,?,?,0,?,?)")
       ->execute(["vasya","",password_hash($pw,PASSWORD_DEFAULT),1789260000000,"127.0.0.1"]);
    echo "vasya created, password: $pw\n";
}
echo "-- containers now --\n";
foreach($db->query("SELECT id,name FROM containers ORDER BY id") as $r){ echo "  {$r["id"]} {$r["name"]}\n"; }
'