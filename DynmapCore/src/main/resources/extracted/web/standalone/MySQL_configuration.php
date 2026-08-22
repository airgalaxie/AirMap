<?php
declare(strict_types=1);

require_once __DIR__ . '/MySQL_funcs.php';
require __DIR__ . '/MySQL_config.php';

try {
    $content = getStandaloneFile('dynmap_config.json');
    if ($content === null) {
        dynmap_json_error(503, 'database-unavailable');
    }

    header('Content-Type: application/json; charset=utf-8');
    echo $content;
} catch (Throwable $error) {
    dynmap_json_error(503, 'database-unavailable');
} finally {
    cleanupDb();
}
