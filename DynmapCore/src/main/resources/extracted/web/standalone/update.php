<?php

$world = $_REQUEST['world'];

header('Content-type: text/plain; charset=utf-8');

if (strpos($world, '/') || strpos($world, '\\')) {
    echo "{ \"error\": \"invalid-world\" }";
    return;
}

if (isset($webpath)) {
    $fname = $webpath . '/standalone/updates_' . $world . '.php';
} else {
    $fname = 'updates_' . $world . '.php';
}

if (!is_readable($fname)) {
    header('HTTP/1.0 404 Not Found');
    return;
}

$lines = file($fname);
if (!$lines) {
    header('HTTP/1.0 404 Not Found');
    return;
}
array_shift($lines);
array_pop($lines);
$json = json_decode(implode(' ', $lines));

$json->loggedin = false;
echo json_encode($json);
