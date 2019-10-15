#ifndef LIBDECSYNC_H
#define LIBDECSYNC_H

#include "libdecsync_api.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void* Decsync;
typedef void* DecsyncEntryWithPath;
typedef void* DecsyncEntry;
typedef void* DecsyncStoredEntry;

/**
 * The `DecSync` class represents an interface to synchronized key-value mappings stored on the file
 * system.
 *
 * The mappings can be synchronized by synchronizing the directory [decsyncDir]. The stored mappings
 * are stored in a conflict-free way. When the same keys are updated independently, the most recent
 * value is taken. This should not cause problems when the individual values contain as little
 * information as possible.
 *
 * Every entry consists of a path, a key and a value. The path is a list of strings which contains
 * the location to the used mapping. This can make interacting with the data easier. It is also used
 * to construct a path in the file system. All characters are allowed in the path. However, other
 * limitations of the file system may apply. For example, there may be a maximum length or the file
 * system may be case insensitive.
 *
 * To update an entry, use the method [decsync_set_entry]. When multiple keys in the same path are
 * updated simultaneous, it is encouraged to use the more efficient methods
 * [decsync_set_entries_for_path] and [decsync_set_entries].
 *
 * To get notified about updated entries, use the method [decsync_execute_all_new_entries] to get
 * all updated entries and call the corresponding listeners. Listeners can be added by the method
 * [decsync_add_listener].
 *
 * Sometimes, updates cannot be execute immediately. For example, if the name of a category is
 * updated when the category does not exist yet, the name cannot be changed. In such cases, the
 * updates have to be executed retroactively. In the example, the update can be executed when the
 * category is created. For such cases, use the method [decsync_execute_stored_entry],
 * [decsync_execute_stored_entries], [decsync_execute_stored_entries_for_path] or
 * [decsync_execute_all_stored_entries_for_path].
 *
 * Finally, to initialize the stored entries to the most recent values, use the method
 * [decsync_init_stored_entries]. This method is almost exclusively used when the application is
 * installed. It is almost always followed by a call to [decsync_execute_stored_entry] or similar.
 */

/**
 * Creates a new [Decsync] instance.
 *
 * @param decsync pointer the value that will be initialized.
 * @param decsync_dir the directory in which the synchronized DecSync files are stored. For the
 * default location, use null or the empty string.
 * @param sync_type the type of data to sync. For example, "rss", "contacts" or "calendars".
 * @param collection an optional collection identifier when multiple instances of the [sync_type]
 * are supported. For example, this is the case for "contacts" and "calendars", but not for "rss".
 * @param own_app_id the unique appId corresponding to the stored data by the application. There
 * must not be two simultaneous instances with the same appId. However, if an application is
 * reinstalled, it may reuse its old appId. In that case, it has to call
 * [decsync_init_stored_entries] and [decsync_execute_stored_entry] or similar. Even if the old
 * appId is not reused, it is still recommended to call these. For the default appId, use
 * [decsync_get_app_id] or [decsync_get_app_id_with_id].
 * @return an error code indicating success or failure:
 *   - 0 for success
 *   - 1 for invalid info
 *   - 2 for unsupported version
 */
inline static int decsync_new(Decsync* decsync, const char* decsync_dir, const char* sync_type, const char* collection, const char* own_app_id) {
    return decsync_so_new(decsync, decsync_dir, sync_type, collection, own_app_id);
}

/**
 * Frees an existing [Decsync] instance.
 */
inline static void decsync_free(Decsync decsync) {
    decsync_so_free(decsync);
}

/**
 * Represents a [DecsyncEntry] with its path.
 *
 * @param path array of null-terminated strings.
 * @param len length of [path].
 * @param key JSON-serialized string.
 * @param value JSON-serialized string.
 */
inline static DecsyncEntryWithPath decsync_entry_with_path_new(const char** path, int len, const char* key, const char* value) {
    return decsync_so_entry_with_path_new(path, len, key, value);
}

/**
 * Frees an existing [DecsyncEntryWithPath].
 */
inline static void decsync_entry_with_path_free(DecsyncEntryWithPath entry_with_path) {
    decsync_so_entry_with_path_free(entry_with_path);
}

/**
 * Represents a key/value pair stored by DecSync. Its datetime property is set to the current
 * datetime. It does not store its path, see [EntryWithPath].
 *
 * @param key JSON-serialized string.
 * @param value JSON-serialized string.
 */
inline static DecsyncEntry decsync_entry_new(const char* key, const char* value) {
    return decsync_so_entry_new(key, value);
}

/**
 * Frees an existing [DecsyncEntry].
 */
inline static void decsync_entry_free(DecsyncEntry entry) {
    decsync_so_entry_free(entry);
}

/**
 * Represents the path and key stored by DecSync. It does not store its value, as it is unknown when
 * retrieving a stored entry.
 *
 * @param path array of null-terminated strings.
 * @param len length of [path].
 * @param key JSON-serialized string.
 */
inline static DecsyncStoredEntry decsync_stored_entry_new(const char** path, int len, const char* key) {
    return decsync_so_stored_entry_new(path, len, key);
}

/**
 * Frees an existing [DecsyncStoredEntry].
 */
inline static void decsync_stored_entry_free(DecsyncStoredEntry stored_entry) {
    decsync_so_stored_entry_free(stored_entry);
}

/**
 * Adds a listener, which describes the actions to execute on some updated entries. When an entry is
 * updated, the function [on_entry_update] is called on the listener whose [subpath] matches. It
 * matches when the given subpath is a prefix of the path of the entry.
 *
 * @param decsync the [Decsync] instance to use.
 * @param subpath array of null-terminated strings.
 * @param len length of [subpath].
 * @param on_entry_update function pointer which the following argument types:
 *   - path: array of null-terminated strings.
 *   - len: length of [path].
 *   - datetime: ISO8601 formatted null-terminated string.
 *   - key: null-terminated string.
 *   - value: null-terminated string.
 *   - extra: extra userdata passed through.
 */
inline static void decsync_add_listener(Decsync decsync, const char** subpath, int len, void (*on_entry_update)(const char** path, int len, const char* datetime, const char* key, const char* value, void* extra)) {
    decsync_so_add_listener(decsync, subpath, len, (void*)on_entry_update);
}

/**
 * Associates the given [value] with the given [key] in the map corresponding to the given [path].
 * This update is sent to synchronized devices.
 *
 * @param decsync the [Decsync] instance to use.
 * @param path array of null-terminated strings.
 * @param len length of [path].
 * @param key JSON-serialized string.
 * @param value JSON-serialized string.
 */
inline static void decsync_set_entry(Decsync decsync, const char** path, int len, const char* key, const char* value) {
    decsync_so_set_entry(decsync, path, len, key, value);
}

/**
 * Like [decsync_set_entry], but allows multiple entries to be set. This is more efficient if
 * multiple entries share the same path.
 *
 * @param decsync the [Decsync] instance to use.
 * @param entries_with_path entries with path which are inserted.
 * @param len length of [entries_with_path].
 */
inline static void decsync_set_entries(Decsync decsync, DecsyncEntryWithPath* entries_with_path, int len) {
    decsync_so_set_entries(decsync, entries_with_path, len);
}

/**
 * Like [decsync_set_entries], but only allows the entries to have the same path. Consequently, it
 * can be slightly more convenient since the path has to be specified just once.
 *
 * @param decsync the [Decsync] instance to use.
 * @param path path to the map in which the entries are inserted.
 * @param len_path length of [path].
 * @param entries entries which are inserted.
 * @param len_entries length of [entries].
 */
inline static void decsync_set_entries_for_path(Decsync decsync, const char** path, int len_path, DecsyncEntry* entries, int len_entries) {
    decsync_so_set_entries_for_path(decsync, path, len_path, entries, len_entries);
}

/**
 * Gets all updated entries and executes the corresponding actions.
 *
 * @param decsync the [Decsync] instance to use.
 * @param extra extra userdata passed to the [listeners].
 */
inline static void decsync_execute_all_new_entries(Decsync decsync, void* extra) {
    decsync_so_execute_all_new_entries(decsync, extra);
}

/**
 * Gets the stored entry in [path] with key [key] and executes the corresponding action, passing
 * extra data [extra] to the listener.
 *
 * @param decsync the [Decsync] instance to use.
 * @param path array of null-terminated strings.
 * @param len length of [path].
 * @param key JSON-serialized string.
 * @param extra extra userdata passed to the listener.
 */
inline static void decsync_execute_stored_entry(Decsync decsync, const char** path, int len, const char* key, void* extra) {
    decsync_so_execute_stored_entry(decsync, path, len, key, extra);
}

/**
 * Like [decsync_execute_stored_entry], but allows multiple entries to be executed. This is more
 * efficient if multiple entries share the same path.
 *
 * @param decsync the [Decsync] instance to use.
 * @param stored_entries entries with path and key to be executed.
 * @param len length of [stored_entries].
 * @param extra extra userdata passed to the listener.
 */
inline static void decsync_execute_stored_entries(Decsync decsync, DecsyncStoredEntry* stored_entries, int len, void* extra) {
    decsync_so_execute_stored_entries(decsync, stored_entries, len, extra);
}

/**
 * Like [decsync_execute_stored_entries], but only allows the stored entries to have the same path.
 * Consequently, it can be slightly more convenient since the path has to be specified just once.
 *
 * @param decsync the [Decsync] instance to use.
 * @param path path to the entries to executes.
 * @param len_path length of [path].
 * @param extra extra userdata passed to the listener.
 * @param keys array of keys to execute, where every key is a JSON-serialized string.
 * @param len_keys length of [keys].
 */
inline static void decsync_execute_stored_entries_for_path(Decsync decsync, const char** path, int len_path, void* extra, const char** keys, int len_keys) {
    decsync_so_execute_stored_entries_for_path(decsync, path, len_path, extra, keys, len_keys);
}

/**
 * Like [decsync_execute_stored_entries_for_path], but ignores the key and executes all entries.
 *
 * @param decsync the [Decsync] instance to use.
 * @param path path of null-terminated strings to the entries to execute.
 * @param len length of [path].
 * @param extra extra userdata passed to the listener.
 */
inline static void decsync_execute_all_stored_entries_for_path(Decsync decsync, const char** path, int len, void* extra) {
    decsync_so_execute_all_stored_entries_for_path(decsync, path, len, extra);
}

/**
 * Initializes the stored entries. This method does not execute any actions. This is often followed
 * with a call to [decsync_execute_stored_entries].
 *
 * @param decsync the [Decsync] instance to use.
 */
inline static void decsync_init_stored_entries(Decsync decsync) {
    decsync_so_init_stored_entries(decsync);
}

/**
 * Returns the most up-to-date appId. This is the appId which has stored the most recent entry. In
 * case of a tie, the appId corresponding to the current application is used, if possible.
 *
 * @param decsync the [Decsync] instance to use.
 * @param app_id buffer to which the resulting appId is written.
 * @param len length of the buffer [app_id], including terminating null character. It should be at
 *   least 256. If it is too short, the result is truncated.
 */
inline static void decsync_latest_app_id(Decsync decsync, char* app_id, int len) {
    decsync_so_latest_app_id(decsync, app_id, len);
}

/**
 * Returns the most up-to-date value stored in the path `["info"]` with key [key], in the given
 * DecSync dir [decsync_dir], sync type [sync_type] and collection [collection]. If no such value is
 * found, the JSON value null is used.
 *
 * @param decsync_dir the path to the main DecSync directory.
 * @param sync_type the type of data to sync. For example, "contacts" or "calendars".
 * @param collection collection identifier.
 * @param key JSON-serialized string.
 * @param value buffer to which the resulting JSON-serialized value is written.
 * @param len length of the buffer [value], including terminating null character. If it is too
 *   short, the result is truncated.
 */
inline static void decsync_get_static_info(const char* decsync_dir, const char* sync_type, const char* collection, const char* key, char* value, int len) {
    decsync_so_get_static_info(decsync_dir, sync_type, collection, key, value, len);
}

/**
 * Checks whether the .decsync-info file in [decsyncDir] is of the right format and contains a
 * supported version. If it does not exist, a new one with version 1 is created.
 *
 * @return an error code indicating success or failure:
 *   - 0 for success
 *   - 1 for invalid info
 *   - 2 for unsupported version
 */
inline static int decsync_check_decsync_info(const char* decsync_dir) {
    return decsync_so_check_decsync_info(decsync_dir);
}

/**
 * Returns a list of DecSync collections inside a [decsync_dir] for a [sync_type]. This function
 * does not apply for sync types with single instances.
 *
 * @param decsync_dir the path to the main DecSync directory.
 * @param sync_type the type of data to sync. For example, "contacts" or "calendars".
 * @param collections array of buffers to which the resulting collections are written. Every buffer
 *   must have a length of 256, including terminating null character.
 * @param max_len length of the array [collections]. If there are more collections, only some are
 *   written to the array.
 * @return the number of written collection identifiers to [collections].
 */
inline static int decsync_list_decsync_collections(const char* decsync_dir, const char* sync_type, const char** collections, int max_len) {
    return decsync_so_list_decsync_collections(decsync_dir, sync_type, collections, max_len);
}

/**
 * Returns the appId of the current device and application combination.
 *
 * @param app_name the name of the application.
 * @param app_id buffer to which the resulting appId is written.
 * @param len length of the buffer [app_id], including terminating null character. It should be at
 *   least 256. If it is too short, the result is truncated.
 */
inline static void decsync_get_app_id(const char* app_name, char* app_id, int len) {
    decsync_so_get_app_id(app_name, app_id, len);
}

/**
 * Like [decsync_get_app_id], but additionally takes an [id] to distinguish different instances on
 * the same device and application.
 *
 * @param app_name the name of the application.
 * @param id integer between 0 and 100000 exclusive.
 * @param app_id buffer to which the resulting appId is written.
 * @param len length of the buffer [app_id], including terminating null character. It should be at
 *   least 256. If it is too short, the result is truncated.
 */
inline static void decsync_get_app_id_with_id(const char* app_name, int id, char* app_id, int len) {
    decsync_so_get_app_id_with_id(app_name, id, app_id, len);
}

#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* LIBDECSYNC_H */
