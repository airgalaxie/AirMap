<?php
declare(strict_types=1);

require_once __DIR__ . '/MySQL_funcs.php';
require __DIR__ . '/MySQL_config.php';

try {
    $path = dynmap_request_string('tile');
    if (!dynmap_validate_path($path)) {
        dynmap_http_error(400, 'Bad tile path');
    }

    $parts = explode('/', $path);
    if (count($parts) !== 4 || !dynmap_validate_name($parts[0]) || !dynmap_validate_name($parts[1])) {
        dynmap_redirect_blank();
    }

    $world = $parts[0];
    $prefix = $parts[1];
    $variant = 'STANDARD';
    if (str_ends_with($prefix, '_day')) {
        $prefix = substr($prefix, 0, -4);
        $variant = 'DAY';
    }

    $file = $parts[3];
    $name = preg_replace('/\.(png|jpg|jpeg|webp)$/i', '', $file);
    $coords = explode('_', (string) $name);
    if (count($coords) === 3 && $coords[0] !== '') {
        $zoom = strlen($coords[0]);
        $x = filter_var($coords[1], FILTER_VALIDATE_INT);
        $y = filter_var($coords[2], FILTER_VALIDATE_INT);
    } elseif (count($coords) === 2) {
        $zoom = 0;
        $x = filter_var($coords[0], FILTER_VALIDATE_INT);
        $y = filter_var($coords[1], FILTER_VALIDATE_INT);
    } else {
        dynmap_redirect_blank();
    }
    if ($x === false || $y === false) {
        dynmap_redirect_blank();
    }

    $db = initDbIfNeeded();
    $stmt = $db->prepare(
        'SELECT t.NewImage,t.Image,t.Format,t.HashCode,t.LastUpdate FROM ' . dynmap_table('Maps') .
        ' m JOIN ' . dynmap_table('Tiles') .
        ' t ON m.ID=t.MapID WHERE m.WorldID=? AND m.MapID=? AND m.Variant=? AND t.x=? AND t.y=? AND t.zoom=?'
    );
    if (!$stmt) {
        throw new RuntimeException('Could not prepare tile query');
    }
    $stmt->bind_param('sssiii', $world, $prefix, $variant, $x, $y, $zoom);
    $stmt->execute();
    $stmt->bind_result($newImage, $image, $format, $hash, $lastUpdate);

    if (!$stmt->fetch()) {
        $stmt->close();
        dynmap_redirect_blank();
    }
    $stmt->close();

    $content = $newImage ?? $image;
    if ($content === null) {
        dynmap_redirect_blank();
    }

    if ((int) $format === 0) {
        header('Content-Type: image/png');
    } elseif ((int) $format === 2) {
        header('Content-Type: image/webp');
    } else {
        header('Content-Type: image/jpeg');
    }
    header('ETag: "' . dechex((int) $hash) . '"');
    header('Last-Modified: ' . gmdate('D, d M Y H:i:s', (int) ((int) $lastUpdate / 1000)) . ' GMT');
    echo $content;
} catch (Throwable $error) {
    dynmap_http_error(503, 'Database Unavailable');
} finally {
    cleanupDb();
}
