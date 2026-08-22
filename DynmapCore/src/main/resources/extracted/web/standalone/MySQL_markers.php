<?php
declare(strict_types=1);

require_once __DIR__ . '/MySQL_funcs.php';
require __DIR__ . '/MySQL_config.php';

function dynmap_face_type_id(string $faceType): ?int
{
    return match ($faceType) {
        '8x8' => 0,
        '16x16' => 1,
        '32x32' => 2,
        'body' => 3,
        default => null,
    };
}

try {
    $path = dynmap_request_string('marker');
    if (!dynmap_validate_path($path)) {
        dynmap_http_error(400, 'Bad marker path');
    }

    $parts = explode('/', $path);
    if ($parts[0] === 'faces') {
        if (count($parts) !== 3 || !str_ends_with($parts[2], '.png')) {
            dynmap_redirect_blank();
        }

        $typeId = dynmap_face_type_id($parts[1]);
        $player = substr($parts[2], 0, -4);
        if ($typeId === null || !dynmap_validate_name($player)) {
            dynmap_redirect_blank();
        }

        $db = initDbIfNeeded();
        $stmt = $db->prepare('SELECT Image FROM ' . dynmap_table('Faces') . ' WHERE PlayerName=? AND TypeID=?');
        if (!$stmt) {
            throw new RuntimeException('Could not prepare face query');
        }
        $stmt->bind_param('si', $player, $typeId);
        $stmt->execute();
        $stmt->bind_result($image);
        if ($stmt->fetch()) {
            header('Content-Type: image/png');
            echo $image;
        } else {
            $stmt->close();
            dynmap_redirect_blank();
        }
        $stmt->close();
        cleanupDb();
        exit;
    }

    if ($parts[0] !== '_markers_' || count($parts) !== 2) {
        dynmap_http_error(400, 'Bad marker path');
    }

    $file = $parts[1];
    if (str_ends_with($file, '.json') && str_starts_with($file, 'marker_')) {
        $world = substr($file, strlen('marker_'), -5);
        if (!dynmap_validate_name($world)) {
            dynmap_json_error(400, 'invalid-world');
        }

        $db = initDbIfNeeded();
        $stmt = $db->prepare('SELECT Content FROM ' . dynmap_table('MarkerFiles') . ' WHERE FileName=?');
        if (!$stmt) {
            throw new RuntimeException('Could not prepare marker file query');
        }
        $stmt->bind_param('s', $world);
        $stmt->execute();
        $stmt->bind_result($content);
        header('Content-Type: application/json; charset=utf-8');
        echo $stmt->fetch() ? (string) $content : '{ }';
        $stmt->close();
        cleanupDb();
        exit;
    }

    if (!str_ends_with($file, '.png')) {
        dynmap_redirect_blank();
    }
    $icon = substr($file, 0, -4);
    if (!dynmap_validate_name($icon)) {
        dynmap_redirect_blank();
    }

    $db = initDbIfNeeded();
    $stmt = $db->prepare('SELECT Image FROM ' . dynmap_table('MarkerIcons') . ' WHERE IconName=?');
    if (!$stmt) {
        throw new RuntimeException('Could not prepare marker icon query');
    }
    $stmt->bind_param('s', $icon);
    $stmt->execute();
    $stmt->bind_result($image);
    if ($stmt->fetch()) {
        header('Content-Type: image/png');
        echo $image;
    } else {
        $stmt->close();
        dynmap_redirect_blank();
    }
    $stmt->close();
} catch (Throwable $error) {
    dynmap_http_error(503, 'Database Unavailable');
} finally {
    cleanupDb();
}
