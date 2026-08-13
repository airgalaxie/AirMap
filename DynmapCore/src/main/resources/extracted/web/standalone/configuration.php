<?php

ob_start();
require 'dynmap_access.php';
ob_end_clean();

$lines = file('dynmap_config.php');
array_shift($lines);
array_pop($lines);
$json = json_decode(implode(' ', $lines));

header('Content-type: text/plain; charset=utf-8');

$json->loggedin = false;
$wcnt = count($json->worlds);
for ($i = 0; $i < $wcnt; $i++) {
    $w = $json->worlds[$i];
    $newworlds[] = $w;
    if ($w != null) {
        $mcnt = count($w->maps);
        $newmaps = array();
        for ($j = 0; $j < $mcnt; $j++) {
            $m = $w->maps[$j];
            $newmaps[] = $m;
        }
        $w->maps = $newmaps;
    }
}
$json->worlds = $newworlds;

echo json_encode($json);
