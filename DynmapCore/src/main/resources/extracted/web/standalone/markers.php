<?php

if (!isset($markerspath)) {
    $markerspath = "../tiles/";
}

//Use this to force specific tiles path, versus using passed value
//$markerspath = 'my-tiles-path';

$path = htmlspecialchars($_REQUEST['marker']);
if ((!isset($path)) || strstr($path, "..")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

$fname = $markerspath . $path;

$parts = explode("/", $path);

if (($parts[0] != "faces") && ($parts[0] != "_markers_")) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Bad marker: " . $path;
    exit();
}

if (!is_readable($fname)) {
    if (strstr($path, ".jpg") || strstr($path, ".png")) {
        $fname = "../images/blank.png";
    } else {
        header('HTTP/1.0 404 Not Found');
        echo "<h1>404 Not Found</h1>";
        echo "Not found: " . $path;
        exit();
    }
}
$fp = fopen($fname, 'rb');
if (strstr($path, ".png")) {
    header("Content-Type: image/png");
} elseif (strstr($path, ".jpg")) {
    header("Content-Type: image/jpeg");
} else {
    header("Content-Type: application/text");
}

header("Content-Length: " . filesize($fname));

fpassthru($fp);
exit;
