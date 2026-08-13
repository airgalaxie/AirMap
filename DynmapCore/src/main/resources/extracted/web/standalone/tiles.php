<?php

if (!isset($tilespath)) {
    $tilespath = "../tiles/";
}

//Use this to force specific tiles path, versus using passed value
//$tilespath = 'my-tiles-path';

$path = htmlspecialchars($_REQUEST['tile']);
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$fname = $tilespath . $path;

$parts = explode("/", $path);
$world = $parts[0];

if (count($parts) > 2) {
    $prefix = $parts[1];
    $plen = strlen($prefix);
    if (($plen > 4) && (substr($prefix, $plen - 4) === "_day")) {
        $prefix = substr($prefix, 0, $plen - 4);
    }
    $mapid = $world . "." . $prefix;
}

if (!is_readable($fname)) {
    if (strstr($path, ".jpg") || strstr($path, ".png")) {
        $fname = "../images/blank.png";
    } else {
        echo "{ \"result\": \"bad-tile\" }";
        exit;
    }
}
$fp = fopen($fname, 'rb');
if (strstr($path, ".png")) {
    header("Content-Type: image/png");
} elseif (strstr($path, ".jpg")) {
    header("Content-Type: image/jpeg");
} elseif (strstr($path, ".webp")) {
    header("Content-Type: image/webp");
} else {
    header("Content-Type: application/text");
}

header("Content-Length: " . filesize($fname));

fpassthru($fp);
exit;
