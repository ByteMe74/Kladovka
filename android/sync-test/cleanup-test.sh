#!/bin/bash
php -r '
$db=new PDO("sqlite:/www/wwwroot/kladovka.dr6ter.ru/data/kladovka.db");
$db->setAttribute(PDO::ATTR_ERRMODE,PDO::ERRMODE_EXCEPTION);
// Убираем тестовые-артефакты С10/К10 (не принадлежат реальным пользователям)
$db->exec("DELETE FROM shelves WHERE name=\"С10\"");
$db->exec("DELETE FROM containers WHERE name=\"К10\"");
// Проверяем состояние
echo "users: "; foreach($db->query("SELECT id,username FROM users ORDER BY id") as $r) echo "{$r["id"]}:{$r["username"]} ";
echo "\nshelves: "; foreach($db->query("SELECT id,name,ownerId FROM shelves ORDER BY id") as $r) echo "{$r["id"]}:{$r["name"]}(ow={$r["ownerId"]}) ";
echo "\ncontainers: "; foreach($db->query("SELECT id,name,ownerId FROM containers ORDER BY id") as $r) echo "{$r["id"]}:{$r["name"]}(ow={$r["ownerId"]}) ";
echo "\nshares: "; foreach($db->query("SELECT * FROM shares") as $r) echo json_encode($r)." ";
echo "\n";
'