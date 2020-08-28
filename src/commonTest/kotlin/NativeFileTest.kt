package org.decsync.library

import kotlin.test.*

fun getMockDir(): NativeFile = NativeFile(MockRealDirectory("DecSync", mutableListOf(), null), null)

fun cleanDir(dir: NativeFile) {
    dir.children().forEach { child ->
        child.deleteRecursive()
    }
}

@ExperimentalStdlibApi
class NativeFileMockTest : NativeFileTest(getMockDir())

@ExperimentalStdlibApi
abstract class NativeFileTest(private val dir: NativeFile) {

    @AfterTest
    fun cleanDirChildren() {
        cleanDir(dir)
    }

    @Test
    fun createFile() {
        val line1 = "abc\n".encodeToByteArray()
        val line2 = "def\n".encodeToByteArray()
        val content = line1 + line2
        val file = dir.child("test")
        assertEquals("test", file.name)
        file.write(content)
        checkContent(content, file)
        checkContent(line2, file, line1.size)
    }

    @Test
    fun createDir() {
        val test = dir.child("test")
        assertEquals("test", test.name)
    }

    @Test
    fun findChildren() {
        val content = "foo".encodeToByteArray()
        dir.child("dir1").child("dir2").child("file").write(content)
        val dir1 = (dir.fileSystemNode as RealDirectory).children(dir).find { it.name == "dir1" }!!
        val dir2 = (dir1.fileSystemNode as RealDirectory).children(dir1).find { it.name == "dir2" }!!
        val file = (dir2.fileSystemNode as RealDirectory).children(dir2).find { it.name == "file" }!!
        checkContent(content, file)
    }

    @Test
    fun isEmpty() {
        checkChildren(0, dir)
    }

    @Test
    fun deleteDirWithReset() = deleteDir(true)

    @Test
    fun deleteDirWithoutReset() = deleteDir(false)

    private fun deleteDir(withReset: Boolean) {
        val dir1 = dir.child("dir1")
        dir1.child("file").write("foo".encodeToByteArray())
        checkChildren(1, dir)

        dir1.deleteRecursive()
        if (withReset) dir.resetCache()

        checkChildren(0, dir)
        checkContent(null, dir.child("file"))
    }

    @Test
    fun deleteFileWithReset() = deleteFile(true)

    @Test
    fun deleteFileWithoutReset() = deleteFile(false)

    private fun deleteFile(withReset: Boolean) {
        val file = dir.child("file")
        file.write("foo".encodeToByteArray())
        checkChildren(1, dir)

        file.deleteRecursive()
        if (withReset) dir.resetCache()

        checkChildren(0, dir)
        checkContent(null, dir.child("file"))
    }

    @Test
    fun deleteNestedWithReset() = deleteNested(true)

    @Test
    fun deleteNestedWithoutReset() = deleteNested(false)

    private fun deleteNested(withReset: Boolean) {
        val content = "bar".encodeToByteArray()
        val dir1 = dir.child("dir1")
        val dir12 = dir1.child("dir12")
        val dir13 = dir1.child("dir13")
        val dir134 = dir13.child("dir134")
        dir1.child("file1").write(content)
        dir12.child("file2a").write(content)
        dir12.child("file2b").write(content)
        dir13.child("file3").write(content)
        dir134.child("file4").write(content)

        checkChildren(1, dir)
        checkChildren(3, dir1)
        checkChildren(2, dir12)
        checkChildren(2, dir13)
        checkChildren(1, dir134)

        dir1.deleteRecursive()
        if (withReset) dir.resetCache()

        checkChildren(0, dir)
    }

    @Test
    fun deleteAndWriteWithReset() = deleteAndWrite(true)

    @Test
    fun deleteAndWriteWithoutReset() = deleteAndWrite(false)

    private fun deleteAndWrite(withReset: Boolean) {
        val line1 = "foo\n".encodeToByteArray()
        val line2 = "bar\n".encodeToByteArray()

        val file1 = dir.child("file")
        file1.write(line1, true)
        checkContent(line1, file1)

        file1.deleteRecursive()
        if (withReset) dir.resetCache()

        val file2 = dir.child("file")
        file2.write(line2, true)
        checkContent(line2, file2)
    }

    @Test
    fun writeEmpty() {
        val line1 = "foo\n".encodeToByteArray()
        val line2 = "bar\n".encodeToByteArray()

        val file1 = dir.child("file")
        file1.write(line1)
        checkContent(line1, file1)
        file1.write(ByteArray(0))
        checkContent(null, file1)
        checkChildren(0, dir)
        val file2 = dir.child("file")
        file2.write(line2)
        checkContent(line2, file2)
    }

    @Test
    fun writeFile() {
        val line1 = "foo\n".encodeToByteArray()
        val line2 = "bar\n".encodeToByteArray()
        val line3 = "baz\n".encodeToByteArray()
        val file = dir.child("file")
        file.write(line1)
        checkContent(line1, file)
        file.write(line2, true)
        checkContent(line1 + line2, file)
        checkContent(line2, file, line1.size)
        file.write(line3, false)
        checkContent(line3, file)
    }

    @Test
    fun onlyAppend() {
        val line1 = "foo\n".encodeToByteArray()
        val line2 = "bar\n".encodeToByteArray()
        val file = dir.child("file")
        file.write(line1, true)
        checkContent(line1, file)
        file.write(line2, true)
        checkContent(line1 + line2, file)
    }

    @Test
    fun overwrite() {
        val line1 = "foobar\n".encodeToByteArray()
        val line2 = "baz\n".encodeToByteArray()
        val file = dir.child("file")
        file.write(line1)
        checkContent(line1, file)
        file.write(line2)
        checkContent(line2, file)
    }

    @Test
    fun lengthFile() {
        val line1 = "foo1\n".encodeToByteArray()
        val line2 = "bar22\n".encodeToByteArray()
        val line3 = "baz333\n".encodeToByteArray()
        val file = dir.child("file")
        file.write(line1)
        assertEquals(line1.size, file.length())
        file.write(line2, true)
        assertEquals(line1.size + line2.size, file.length())
        file.write(line3, false)
        assertEquals(line3.size, file.length())
    }

    @Test
    fun sameParent() {
        val dirName = "parent"
        val name1 = "file1"
        val name2 = "file2"
        val text1 = "foo".encodeToByteArray()
        val text2 = "bar".encodeToByteArray()
        val parent1 = dir.child(dirName)
        val parent2 = dir.child(dirName)
        val file1 = parent1.child(name1)
        val file2 = parent2.child(name2)
        file1.write(text1)
        file2.write(text2)
        checkContent(text1, parent2.child(name1))
        checkContent(text2, parent1.child(name2))
    }

    private fun checkContent(expected: ByteArray?, file: NativeFile, readBytes: Int = 0) {
        val actual = file.read(readBytes)
        val equal = when {
            expected == null && actual == null -> true
            expected == null || actual == null -> false
            else -> expected.contentEquals(actual)
        }
        assertTrue(equal, "Expected: ${expected?.toList()}, actual ${actual?.toList()}")
    }

    private fun checkChildren(expected: Int, file: NativeFile) {
        val children = file.children().filter { it.fileSystemNode is RealNode }
        assertEquals(expected, children.size, "${children.map { it.name }}")
    }
}