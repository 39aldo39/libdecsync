#include <libdecsync.h>
#include <iostream>
#include <map>
#include <string>
#include <thread>
#include <vector>

// Very basic tests, mostly to make sure the bindings are correct

typedef std::vector<std::string> Path;
typedef std::pair<Path, std::string> Key;
typedef std::map<Key, std::string> Extra;

void listener(const char** path, const int len, const char* datetime,
              const char* key, const char* value, void* extra_void) {
	Extra* extra = static_cast<Extra*>(extra_void);
	Path pathVector;
	for (int i = 0; i < len; ++i) {
		pathVector.push_back(path[i]);
	}
	(*extra)[{pathVector, key}] = value;
}

bool listener_with_success(const char** path, const int len, const char* datetime,
              const char* key, const char* value, void* extra_void) {
	Extra* extra = static_cast<Extra*>(extra_void);
	Path pathVector;
	for (int i = 0; i < len; ++i) {
		pathVector.push_back(path[i]);
	}
	(*extra)[{pathVector, key}] = value;
	return true;
}

int test_instance() {
	Decsync decsync;
	int error = decsync_new(&decsync, ".tests/decsync_instance", "sync-type", nullptr, "app-id");
	if (error) {
		std::cout << "Test failed: decsync_new (" << error << ")" << std::endl;
		return 1;
	}
	Extra extra;

	const char* path0[0] {};
	decsync_add_listener(decsync, path0, 0, listener);
	decsync_add_listener_with_success(decsync, path0, 0, listener_with_success);

	const char* path1[2] {"foo1", "bar1"};
	Path path1Vector {"foo1", "bar1"};
	decsync_set_entry(decsync, path1, 2, "\"key1\"", "\"value1 ☺\"");

	const char* path2[2] {"foo2", "bar2"};
	Path path2Vector {"foo2", "bar2"};
	DecsyncEntryWithPath entryWithPath = decsync_entry_with_path_new(path2, 2, "\"key2\"", "\"value2\"");
	DecsyncEntryWithPath entriesWithPath[1] {entryWithPath};
	decsync_set_entries(decsync, entriesWithPath, 1);
	decsync_entry_with_path_free(entryWithPath);

	const char* path3[2] {"foo3", "bar3"};
	Path path3Vector {"foo3", "bar3"};
	DecsyncEntry entry = decsync_entry_new("\"key3\"", "\"value3\"");
	DecsyncEntry entries[1] {entry};
	decsync_set_entries_for_path(decsync, path3, 2, entries, 1);
	decsync_entry_free(entry);

	decsync_execute_all_new_entries(decsync, &extra);

	decsync_execute_stored_entry(decsync, path1, 2, "\"key1\"", &extra);
	DecsyncStoredEntry storedEntry = decsync_stored_entry_new(path2, 2, "\"key2\"");
	DecsyncStoredEntry storedEntries[1] {storedEntry};
	decsync_execute_stored_entries(decsync, storedEntries, 1, &extra);
	decsync_stored_entry_free(storedEntry);
	const char* keys3[1] {"\"key3\""};
	decsync_execute_stored_entries_for_path_exact(decsync, path3, 2, &extra, keys3, 1);

	std::string value1 = extra[{path1Vector, "\"key1\""}];
	if (value1 != "\"value1 ☺\"") {
		std::cout << "Test failed: first key1 (" << value1 << ")" << std::endl;
		return 1;
	}
	std::string value2 = extra[{path2Vector, "\"key2\""}];
	if (value2 != "\"value2\"") {
		std::cout << "Test failed: first key2 (" << value2 << ")" << std::endl;
		return 1;
	}
	std::string value3 = extra[{path3Vector, "\"key3\""}];
	if (value3 != "\"value3\"") {
		std::cout << "Test failed: first key3 (" << value3 << ")" << std::endl;
		return 1;
	}

	extra.clear();

	decsync_execute_all_stored_entries_for_path_exact(decsync, path1, 2, &extra);
	const char* keys2[1] {"\"key2\""};
	decsync_execute_stored_entries_for_path_prefix(decsync, path2, 2, &extra, keys2, 1);
	decsync_execute_all_stored_entries_for_path_prefix(decsync, path3, 2, &extra);

	value1 = extra[{path1Vector, "\"key1\""}];
	if (value1 != "\"value1 ☺\"") {
		std::cout << "Test failed: second key1 (" << value1 << ")" << std::endl;
		return 1;
	}
	value2 = extra[{path2Vector, "\"key2\""}];
	if (value2 != "\"value2\"") {
		std::cout << "Test failed: second key2 (" << value2 << ")" << std::endl;
		return 1;
	}
	value3 = extra[{path3Vector, "\"key3\""}];
	if (value3 != "\"value3\"") {
		std::cout << "Test failed: second key3 (" << value3 << ")" << std::endl;
		return 1;
	}

	decsync_init_stored_entries(decsync);
	char latestAppId[256];
	decsync_latest_app_id(decsync, latestAppId, 256);
	if (std::string(latestAppId) != "app-id") {
		std::cout << "Test failed: latest_app_id (" << latestAppId << ")" << std::endl;
		return 1;
	}

	return 0;
}

int test_static() {
	Decsync decsync;
	int error = decsync_new(&decsync, ".tests/decsync_static", "sync-type", "collection", "app-id");
	if (error) {
		std::cout << "Test failed: static decsync_new (" << error << ")" << std::endl;
		return 1;
	}
	const char* path[1] {"info"};
	decsync_set_entry(decsync, path, 1, "\"name\"", "\"Foo\"");

	char value[256];
	decsync_get_static_info(".tests/decsync_static", "sync-type", "collection", "\"name\"", value, 256);
	if (std::string(value) != "\"Foo\"") {
		std::cout << "Test failed: get_static_info[name] (" << value << ")" << std::endl;
		return 1;
	}

	decsync_get_static_info(".tests/decsync_static", "sync-type", "collection", "\"color\"", value, 256);
	if (std::string(value) != "null") {
		std::cout << "Test failed: get_static_info[value] (" << value << ")" << std::endl;
		return 1;
	}

	char collections[256][256];
	int len = decsync_list_collections(".tests/decsync_static", "sync-type", collections, 256);
	if (len != 1) {
		std::cout << "Test failed: list_collections.len (" << len << ")" << std::endl;
		return 1;
	}
	if (std::string(collections[0]) != "collection") {
		std::cout << "Test failed: list_collections (" << collections[0] << ")" << std::endl;
		return 1;
	}

	char appId[256];
	decsync_generate_app_id("app", true, appId, 256);
	decsync_get_app_id("app", appId, 256);
	decsync_get_app_id_with_id("app", 12345, appId, 256);

	return 0;
}

// Test whether we can use the DecSync from another thread
int test_thread() {
	Decsync decsync;
	int error = decsync_new(&decsync, ".tests/decsync_thread", "sync-type", nullptr, "app-id");
	if (error) {
		std::cout << "Test failed: decsync_new (" << error << ")" << std::endl;
		return 1;
	}
	const char* path0[0] {};
	decsync_add_listener(decsync, path0, 0, listener);
	decsync_init_done(decsync);

	std::thread thread([decsync]{
		const char* path1[2] {"foo1", "bar1"};
		decsync_set_entry(decsync, path1, 2, "\"key1\"", "\"value1\"");
	});
	thread.join();

	return 0;
}

int print_result() {
	std::cout << "Tests successful!" << std::endl;
	return 0;
}

int main() {
	return test_instance() || test_static() || test_thread() || print_result();
}
