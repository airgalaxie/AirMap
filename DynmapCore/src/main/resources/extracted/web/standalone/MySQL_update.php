<?php
declare(strict_types=1);

require_once __DIR__ . '/MySQL_funcs.php';
require __DIR__ . '/MySQL_config.php';

try {
    $world = dynmap_request_string('world');
    if (!dynmap_validate_name($world)) {
        dynmap_json_error(400, 'invalid-world');
    }

    $content = getStandaloneFile('dynmap_' . $world . '.json');
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
