#!/usr/bin/env python3

"""
libdecsync.py

Copyright (C) 2019 Aldo Gunsing

This library is free software; you can redistribute it and/or modify it
under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation.

This library is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with this library; if not, see <http://www.gnu.org/licenses/>.
"""

from __future__ import print_function

from ctypes import *
import json
import sys

class DecsyncException(Exception):
    pass

_libdecsync = CDLL("libdecsync.so")

def _errcheckDecsync(result, func, args):
    if result != 0:
        if result == 1:
            raise DecsyncException("DecSync: Invalid .decsync-info")
        elif result == 2:
            raise DecsyncException("DecSync: Unsupported DecSync version")
        else:
            raise DecsyncException("DecSync: Unknown error")

_decsync_new_c = _libdecsync.decsync_so_new
_decsync_new_c.argtypes = [POINTER(c_void_p), c_char_p, c_char_p, c_char_p, c_char_p]
_decsync_new_c.restype = c_int
_decsync_new_c.errcheck = _errcheckDecsync

_decsync_free_c = _libdecsync.decsync_so_free
_decsync_free_c.argtypes = [c_void_p]
_decsync_free_c.restype = None

_entry_with_path_new_c = _libdecsync.decsync_so_entry_with_path_new
_entry_with_path_new_c.argtypes = [POINTER(c_char_p), c_int, c_char_p, c_char_p]
_entry_with_path_new_c.restype = c_void_p

_entry_with_path_free_c = _libdecsync.decsync_so_entry_with_path_free
_entry_with_path_free_c.argtypes = [c_void_p]
_entry_with_path_free_c.restype = None

_entry_new_c = _libdecsync.decsync_so_entry_new
_entry_new_c.argtypes = [c_char_p, c_char_p]
_entry_new_c.restype = c_void_p

_enrty_free_c = _libdecsync.decsync_so_entry_free
_enrty_free_c.argtypes = [c_void_p]
_enrty_free_c.restype = None

_stored_entry_new_c = _libdecsync.decsync_so_stored_entry_new
_stored_entry_new_c.argtypes = [POINTER(c_char_p), c_int, c_char_p]
_stored_entry_new_c.restype = c_void_p

_stored_entry_free_c = _libdecsync.decsync_so_stored_entry_free
_stored_entry_free_c.argtypes = [c_void_p]
_stored_entry_free_c.restype = c_void_p

_add_listener_c = _libdecsync.decsync_so_add_listener
_on_entry_update_t = CFUNCTYPE(None, POINTER(c_char_p), c_int, c_char_p, c_char_p, c_char_p, py_object)
_add_listener_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, _on_entry_update_t]
_add_listener_c.restype = None

_set_entry_c = _libdecsync.decsync_so_set_entry
_set_entry_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, c_char_p, c_char_p]
_set_entry_c.restype = None

_set_entries_c = _libdecsync.decsync_so_set_entries
_set_entries_c.argtypes = [c_void_p, POINTER(c_void_p), c_int]
_set_entries_c.restype = None

_set_entries_for_path_c = _libdecsync.decsync_so_set_entries_for_path
_set_entries_for_path_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, POINTER(c_void_p), c_int]
_set_entries_for_path_c.restype = None

_execute_all_new_entries_c = _libdecsync.decsync_so_execute_all_new_entries
_execute_all_new_entries_c.argtypes = [c_void_p, py_object]
_execute_all_new_entries_c.restype = None

_execute_stored_entry_c = _libdecsync.decsync_so_execute_stored_entry
_execute_stored_entry_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, c_char_p, py_object]
_execute_stored_entry_c.restype = None

_execute_stored_entries_c = _libdecsync.decsync_so_execute_stored_entries
_execute_stored_entries_c.argtypes = [c_void_p, POINTER(c_void_p), c_int, py_object]
_execute_stored_entries_c.restype = None

_execute_stored_entries_for_path_c = _libdecsync.decsync_so_execute_stored_entries_for_path
_execute_stored_entries_for_path_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, py_object, POINTER(c_char_p), c_int]
_execute_stored_entries_for_path_c.restype = None

_execute_all_stored_entries_for_path_c = _libdecsync.decsync_so_execute_all_stored_entries_for_path
_execute_all_stored_entries_for_path_c.argtypes = [c_void_p, POINTER(c_char_p), c_int, py_object]
_execute_all_stored_entries_for_path_c.restype = None

_init_stored_entries_c = _libdecsync.decsync_so_init_stored_entries
_init_stored_entries_c.argtypes = [c_void_p]
_init_stored_entries_c.restype = None

_latest_app_id_c = _libdecsync.decsync_so_latest_app_id
_latest_app_id_c.argtypes = [c_void_p, c_char_p, c_int]
_latest_app_id_c.restype = None

_get_static_info_c = _libdecsync.decsync_so_get_static_info
_get_static_info_c.argtypes = [c_char_p, c_char_p, c_char_p, c_char_p, c_char_p, c_int]
_get_static_info_c.restype = None

_list_decsync_collections_c = _libdecsync.decsync_so_list_decsync_collections
_list_decsync_collections_c.argtypes = [c_char_p, c_char_p, POINTER(c_char_p), c_int]
_list_decsync_collections_c.restype = c_int

_get_app_id_c = _libdecsync.decsync_so_get_app_id
_get_app_id_c.argtypes = [c_char_p, c_char_p, c_int]
_get_app_id_c.restype = None

_get_app_id_with_id_c = _libdecsync.decsync_so_get_app_id_with_id
_get_app_id_with_id_c.argtypes = [c_char_p, c_int, c_char_p, c_int]
_get_app_id_with_id_c.restype = None

def _c_array(c_type, xs):
    return [(c_type * len(xs))(*xs), len(xs)]

def _c_path(path):
    return _c_array(c_char_p, list(map(str.encode, path)))

def _c_return_string(func, *args, size=256):
    result = create_string_buffer(size)
    func(*args, result, size)
    return result.value.decode()

class Decsync:
    """
    The `DecSync` class represents an interface to synchronized key-value mappings stored on the
    file system.

    The mappings can be synchronized by synchronizing the directory [decsync_dir]. The stored
    mappings are stored in a conflict-free way. When the same keys are updated independently, the
    most recent value is taken. This should not cause problems when the individual values contain as
    little information as possible.

    Every entry consists of a path, a key and a value. The path is a list of strings which contains
    the location to the used mapping. This can make interacting with the data easier. It is also
    used to construct a path in the file system. All characters are allowed in the path. However,
    other limitations of the file system may apply. For example, there may be a maximum length or
    the file system may be case insensitive.

    To update an entry, use the method [set_entry]. When multiple keys in the same path are updated
    simultaneous, it is encouraged to use the more efficient methods [set_entries_for_path] and
    [set_entries].

    To get notified about updated entries, use the method [execute_all_new_entries] to get all
    updated entries and call the corresponding listeners. Listeners can be added by the method
    [add_listener].

    Sometimes, updates cannot be execute immediately. For example, if the name of a category is
    updated when the category does not exist yet, the name cannot be changed. In such cases, the
    updates have to be executed retroactively. In the example, the update can be executed when the
    category is created. For such cases, use the method [execute_stored_entry],
    [execute_stored_entries] or [execute_stored_entries_for_path].

    Finally, to initialize the stored entries to the most recent values, use the method
    [init_stored_entries]. This method is almost exclusively used when the application is installed.
    It is almost always followed by a call to [execute_stored_entry] or similar.

    """

    class EntryWithPath:
        """Represents an [Entry] with its path"""

        def __init__(self, path, key, value):
            """
            :type path: list(str)
            :type key: json
            :type value: json

            """
            self.ptr = _entry_with_path_new_c(*_c_path(path), json.dumps(key).encode(), json.dumps(value).encode())

        def __del__(self):
            _entry_with_path_free_c(self.ptr)

    class Entry:
        """Represents a key/value pair stored by DecSync. Its datetime property is set to the current datetime. It does not store its path, see [EntryWithPath]."""

        def __init__(self, key, value):
            """
            :type key: json
            :type value: json

            """
            self.ptr = _entry_new_c(json.dumps(key).encode(), json.dumps(value).encode())

        def __del__(self):
            _enrty_free_c(self.ptr)

    class StoredEntry:
        """Represents the path and key stored by DecSync. It does not store its value, as it is unknown when retrieving a stored entry."""

        def __init__(self, path, key):
            """
            :type path: list(str)
            :type key: json

            """
            self.ptr = _stored_entry_new_c(*_c_path(path), json.dumps(key).encode())

        def __del__(self):
            _stored_entry_free_c(self.ptr)

    def __init__(self, decsync_dir, sync_type, collection, own_app_id):
        """Creates a new [Decsync] instance

        :param str decsync_dir: the directory in which the synchronized DecSync files are stored. For the
            default location, use [get_default_decsync_dir].
        :param str sync_type: the type of data to sync. For example, "rss", "contacts" or "calendars".
        :param collection: an optional collection identifier when multiple instances of the [sync_type] are
            supported. For example, this is the case for "contacts" and "calendars", but not for "rss".
        :type collection: str or None
        :param str own_app_id: the unique appId corresponding to the stored data by the application.
            There must not be two simultaneous instances with the same appId. However, if an
            application is reinstalled, it may reuse its old appId. In that case, it has to call
            [init_stored_entries] and [execute_stored_entry] or similar. Even if the old appId is
            not reused, it is still recommended to call these. For the default appId, use
            [get_app_id].
        :throws DecsyncException: If a DecSync configuration error occured.

        """
        self.ptr = c_void_p()
        self.listeners = []
        if decsync_dir is None:
            decsync_dir = ""
        if collection is None:
            collection = ""
        _decsync_new_c(byref(self.ptr), decsync_dir.encode(), sync_type.encode(), collection.encode(), own_app_id.encode())

    def add_listener(self, subpath, on_entry_update):
        """
        Adds a listener, which describes the actions to execute on some updated entries.

        When an entry is updated, the function [on_entry_update] is called on the listener whose
        [subpath] matches. It matches when the given subpath is a prefix of the path of the entry.

        :type subpath: list(str)
        :param on_entry_update: function pointer which the following argument types:
          - path: list of strings
          - datetime: ISO8601 formatted string
          - key: json
          - value: json
          - extra: extra userdata passed through

        """
        @_on_entry_update_t
        def on_entry_update_c(path_c, path_len, datetime, key_string, value_string, extra):
            path = [path_c[i].decode() for i in range(path_len)]
            key = json.loads(key_string)
            value = json.loads(value_string)
            try:
                on_entry_update(path, datetime.decode(), key, value, extra)
            except Exception as e:
                print(e, file=sys.stderr)
        self.listeners.append(on_entry_update_c) # Keep a reference around
        _add_listener_c(self.ptr, *_c_path(subpath), on_entry_update_c)

    def __del__(self):
        _decsync_free_c(self.ptr)

    def set_entry(self, path, key, value):
        """
        Associates the given [value] with the given [key] in the map corresponding to the given [path].
        This update is sent to synchronized devices.

        :type path: list(str)
        :type key: json
        :type value: json

        """
        _set_entry_c(self.ptr, *_c_path(path), json.dumps(key).encode(), json.dumps(value).encode())

    def set_entries(self, entries_with_path):
        """
        Like [decsync_set_entry], but allows multiple entries to be set. This is more efficient if
        multiple entries share the same path.

        :param entries_with_path: entries with path which are inserted.
        :type entries_with_path: list(EntryWithPath)

        """
        entries_with_path_c = _c_array(c_void_p, list(map(lambda x: x.ptr, entries_with_path)))
        _set_entries_c(self.ptr, *entries_with_path_c)

    def set_entries_for_path(self, path, entries):
        """
        Like [decsync_set_entries], but only allows the entries to have the same path. Consequently, it
        can be slightly more convenient since the path has to be specified just once.

        :param list(str) path: path to the map in which the entries are inserted.
        :param entries: entries which are inserted.
        :type entries: list(Entry)

        """
        entries_c = _c_array(c_void_p, list(map(Entry.ptr, entries)))
        _set_entries_for_path_c(self.ptr, *_c_path(path), *entries_c)

    def execute_all_new_entries(self, extra):
        """
        Gets all updated entries and executes the corresponding actions.

        :param extra: extra userdata passed to the [listeners].

        """
        _execute_all_new_entries_c(self.ptr, extra)

    def execute_stored_entry(self, path, key, extra):
        """
        Gets the stored entry in [path] with key [key] and executes the corresponding action, passing
        extra data [extra] to the listener.

        :type path: list(str)
        :type key: json
        :param extra: extra userdata passed to the listener.

        """
        _execute_stored_entry_c(self.ptr, *_c_path(path), json.dumps(key).encode(), extra)

    def execute_stored_entries(self, stored_entries, extra):
        """
        Like [decsync_execute_stored_entry], but allows multiple entries to be executed. This is more
        efficient if multiple entries share the same path.

        :param stored_entries: entries with path and key to be executed.
        :type stored_entries: list(StoredEntry)
        :param extra: extra userdata passed to the listener.

        """
        stored_entries_c = _c_array(c_void_p, list(map(StoredEntry.ptr, stored_entries)))
        _execute_stored_entries_c(self.ptr, *stored_entries_c, extra)

    def execute_stored_entries_for_path(self, path, extra, keys=None):
        """
        Like [decsync_execute_stored_entries], but only allows the stored entries to have the same path.
        Consequently, it can be slightly more convenient since the path has to be specified just once.

        :type path: list(str)
        :param extra: extra userdata passed to the listener.
        :param keys: list of keys to execute, where every key is a JSON-serialized string. When None, all keys are executed.
        :type keys: list(json) or None

        """
        if keys is None:
            _execute_all_stored_entries_for_path_c(self.ptr, *_c_path(path), extra)
        else:
            keys_c = _c_array(c_char_p, [json.dumps(key).encode() for key in keys])
            _execute_stored_entries_for_path_c(self.ptr, *_c_path(path), extra, *keys_c)

    def init_stored_entries(self):
        """
        Initializes the stored entries. This method does not execute any actions. This is often followed
        with a call to [decsync_execute_stored_entries].

        """
        _init_stored_entries_c(self.ptr)

    def latest_app_id(self):
        """
        Returns the most up-to-date appId. This is the appId which has stored the most recent entry. In
        case of a tie, the appId corresponding to the current application is used, if possible.

        :rtype: str

        """
        return _c_return_string(_latest_app_id_c, self.ptr)

    @staticmethod
    def get_static_info(decsync_dir, sync_type, collection, key):
        """
        Returns the most up-to-date value stored in the path `["info"]` with key [key], in the given
        DecSync dir [decsync_dir], sync type [sync_type] and collection [collection]. If no such value is
        found, the JSON value null is used.

        :param str decsync_dir: the path to the main DecSync directory.
        :param str sync_type: the type of data to sync. For example, "contacts" or "calendars".
        :param str collection: collection identifier.
        :type key: json
        :returns: the resulting JSON value, truncated to 256 bytes.
        :rtype: json

        """
        return json.loads(_c_return_string(_get_static_info_c, decsync_dir.encode(), sync_type.encode(), collection.encode(), json.dumps(key).encode()))

    @staticmethod
    def list_collections(decsync_dir, sync_type, max_len=256):
        """
        Returns a list of DecSync collections inside a [decsync_dir] for a [sync_type]. This function
        does not apply for sync types with single instances.

        :param str decsync_dir: the path to the main DecSync directory.
        :param str sync_type: the type of data to sync. For example, "contacts" or "calendars".
        :param int max_len: maximum length of the resulting list. If there are more collections, only some are
            written to the array. Default value: 256.
        :rtype: list(str)

        """
        string_buffers = [create_string_buffer(256) for i in range(max_len)]
        pointers = _c_array(c_char_p, list(map(addressof, string_buffers)))
        num_collections = _list_decsync_collections_c(decsync_dir.encode(), sync_type.encode(), *pointers)
        return [string_buffers[i].value.decode() for i in range(num_collections)]

    @staticmethod
    def get_app_id(app_name, id=None):
        """
        Returns the appId of the current device and application combination.

        :param str appName: the name of the application.
        :param id: an optional integer (between 0 and 100000 exclusive) to distinguish different instances
            on the same device and application. Default value: None.
        :type id: int or None
        :rtype: str

        """
        if id is None:
            return _c_return_string(_get_app_id_c, app_name.encode())
        else:
            return _c_return_string(_get_app_id_with_id_c, app_name.encode(), id)
