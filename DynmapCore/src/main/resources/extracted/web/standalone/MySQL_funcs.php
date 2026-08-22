<?php
declare(strict_types=1);

ini_set('display_errors', '0');
ini_set('log_errors', '1');

mysqli_report(MYSQLI_REPORT_OFF);

function dynmap_request_string(string $key, string $default = ''): string
{
    $value = $_REQUEST[$key] ?? $default;
    if (is_array($value)) {
        return $default;
    }
    return (string) $value;
}

function dynmap_request_int(string $key, int $default = 0): int
{
    $value = dynmap_request_string($key, (string) $default);
    return preg_match('/^-?\d+$/', $value) === 1 ? (int) $value : $default;
}

function dynmap_validate_path(string $path): bool
{
    return $path !== '' && strpos($path, '..') === false && strpos($path, '\\') === false;
}

function dynmap_validate_name(string $name): bool
{
    return $name !== '' && strpos($name, '..') === false && strpos($name, '/') === false && strpos($name, '\\') === false && strpos($name, "\0") === false;
}

function dynmap_table(string $suffix): string
{
    global $dbprefix;
    $prefix = isset($dbprefix) ? (string) $dbprefix : '';
    if (preg_match('/^[A-Za-z0-9_]*$/', $prefix) !== 1) {
        throw new RuntimeException('Invalid database table prefix');
    }
    return $prefix . $suffix;
}

function dynmap_database_name(): string
{
    global $dbname;
    $name = isset($dbname) ? (string) $dbname : '';
    $pos = strpos($name, '?');
    return $pos === false ? $name : substr($name, 0, $pos);
}

function initDbIfNeeded(): mysqli
{
    global $db, $dbhost, $dbuserid, $dbpassword, $dbport;

    if (isset($db) && $db instanceof mysqli) {
        return $db;
    }

    $host = isset($dbhost) ? (string) $dbhost : 'localhost';
    $user = isset($dbuserid) ? (string) $dbuserid : '';
    $password = isset($dbpassword) ? (string) $dbpassword : '';
    $port = isset($dbport) ? (int) $dbport : 3306;

    $db = mysqli_init();
    if (!$db instanceof mysqli) {
        throw new RuntimeException('Could not initialize mysqli');
    }
    if (!$db->real_connect($host, $user, $password, dynmap_database_name(), $port)) {
        throw new RuntimeException('Error opening database');
    }
    $db->set_charset('utf8mb4');
    return $db;
}

function cleanupDb(): void
{
    global $db;
    if (isset($db) && $db instanceof mysqli) {
        $db->close();
    }
    $db = null;
}

function dynmap_http_error(int $status, string $message, string $contentType = 'text/plain; charset=utf-8'): never
{
    http_response_code($status);
    header('Content-Type: ' . $contentType);
    echo $message;
    cleanupDb();
    exit;
}

function dynmap_json_error(int $status, string $error): never
{
    dynmap_http_error($status, '{"error":"' . addslashes($error) . '"}', 'application/json; charset=utf-8');
}

function dynmap_redirect_blank(): never
{
    header('Location: ../images/blank.png');
    cleanupDb();
    exit;
}

function dynmap_server_id(): int
{
    global $serverid;
    if (!isset($serverid)) {
        $serverid = dynmap_request_int('serverid', 0);
    }
    return (int) $serverid;
}

function getStandaloneFileByServerId(string $fname, int $sid): ?string
{
    $db = initDbIfNeeded();
    $stmt = $db->prepare('SELECT Content FROM ' . dynmap_table('StandaloneFiles') . ' WHERE FileName=? AND ServerID=?');
    if (!$stmt) {
        throw new RuntimeException('Could not prepare standalone file query');
    }
    $stmt->bind_param('si', $fname, $sid);
    $stmt->execute();
    $stmt->store_result();
    $stmt->bind_result($content);
    $result = $stmt->fetch() ? (string) $content : null;
    $stmt->close();
    return $result;
}

function getStandaloneFile(string $fname): ?string
{
    return getStandaloneFileByServerId($fname, dynmap_server_id());
}

function updateStandaloneFileByServerId(string $fname, int $sid, string $content): bool
{
    $db = initDbIfNeeded();
    $stmt = $db->prepare('UPDATE ' . dynmap_table('StandaloneFiles') . ' SET Content=? WHERE FileName=? AND ServerID=?');
    if (!$stmt) {
        throw new RuntimeException('Could not prepare standalone file update');
    }
    $stmt->bind_param('ssi', $content, $fname, $sid);
    $ok = $stmt->execute();
    $affected = $stmt->affected_rows;
    $stmt->close();
    if (!$ok || $affected <= 0) {
        return insertStandaloneFileByServerId($fname, $sid, $content);
    }
    return true;
}

function updateStandaloneFile(string $fname, string $content): bool
{
    return updateStandaloneFileByServerId($fname, dynmap_server_id(), $content);
}

function insertStandaloneFileByServerId(string $fname, int $sid, string $content): bool
{
    $db = initDbIfNeeded();
    $stmt = $db->prepare('INSERT INTO ' . dynmap_table('StandaloneFiles') . ' (Content,FileName,ServerID) VALUES (?,?,?)');
    if (!$stmt) {
        throw new RuntimeException('Could not prepare standalone file insert');
    }
    $stmt->bind_param('ssi', $content, $fname, $sid);
    $ok = $stmt->execute();
    $stmt->close();
    return $ok;
}

function insertStandaloneFile(string $fname, string $content): bool
{
    return insertStandaloneFileByServerId($fname, dynmap_server_id(), $content);
}
