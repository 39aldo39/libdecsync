package org.decsync.library

import kotlin.test.*

@ExperimentalStdlibApi
class DecsyncFileMockTest : DecsyncFileTest(getMockDir())

@ExperimentalStdlibApi
abstract class DecsyncFileTest(nativeFile: NativeFile) {
    private val dir = DecsyncFile(nativeFile)

    @AfterTest
    fun cleanDirChildren() {
        cleanDir(dir.file)
    }

    @Test
    fun readWrite() {
        val file = dir.child("file")
        val line1 = "foo"
        val line2 = "bar"
        val line3 = "baz"
        val lines = listOf(line1, line2)
        file.writeLines(lines)
        assertEquals(lines, file.readLines())
        assertEquals(listOf(line2), file.readLines( line1.encodeToByteArray().size + 1))
        file.writeLines(listOf(line3), true)
        assertEquals(lines + line3, file.readLines())
    }

    @Test
    fun readWriteNewFile() {
        val writeFile = dir.child("file")
        val line1 = "foo"
        val line2 = "bar"
        val lines = listOf(line1, line2)
        writeFile.writeLines(lines)
        val readFile = dir.child("file")
        assertEquals(lines, readFile.readLines())
        assertEquals(listOf(line2), readFile.readLines( line1.encodeToByteArray().size + 1))
    }

    @Test
    fun readWriteUnicode() {
        val file = dir.child("file")
        val line1 = "foo\u263A"
        val line2 = "bar\uD83C\uDF08"
        val lines = listOf(line1, line2)
        file.writeLines(lines)
        assertEquals(lines, file.readLines())
        assertEquals(listOf(line2), file.readLines( line1.encodeToByteArray().size + 1))
    }

    @Test
    fun readWriteText() {
        val file = dir.child("file")
        val text1 = "foobar"
        val text2 = "baz"
        assertEquals(null, file.readText())
        file.writeText(text1)
        assertEquals(text1, file.readText())
        file.writeText(text2)
        assertEquals(text2, file.readText())
    }

    @Test
    fun readWriteBlank() {
        val linesWithBlank = listOf("a b\tc\r", "\t\r", "def")
        val linesWithoutBlank = listOf("a b\tc\r", "def")
        val file = dir.child("file")
        file.writeLines(linesWithBlank)
        assertEquals(linesWithoutBlank, file.readLines())
    }

    @Test
    fun unicodeName() {
        val name = "file\uD83C\uDF08"
        val file = dir.child(name)
        val text = "foo"
        file.writeText(text)
        assertEquals(Url.encode(name), file.file.name)
        assertEquals(text, file.readText())
    }

    @Test
    fun hiddenFile() {
        val name = ".file\uD83C\uDF08"
        val hiddenFile = dir.hiddenChild(name)
        val hiddenText = "foo"
        val file = dir.child(".$name")
        val text = "bar"
        hiddenFile.writeText(hiddenText)
        file.writeText(text)
        assertEquals("." + Url.encode(name), hiddenFile.file.name)
        assertEquals(hiddenText, hiddenFile.readText())
        assertEquals(Url.encode(".$name"), file.file.name)
        assertEquals(text, file.readText())
    }

    @Test
    fun chainedChildren() {
        val name1 = "foo"
        val name2 = "bar"
        val name3 = "baz"
        assertEquals(dir.file, dir.child().file)
        assertEquals(dir.child(name1).child(name2).child(name3).file, dir.child(name1, name2, name3).file)
    }

    @Test
    fun sameParent() {
        val dirName = "parent"
        val name1 = "file1"
        val name2 = "file2"
        val text1 = "foo"
        val text2 = "bar"
        val parent1 = dir.child(dirName)
        val parent2 = dir.child(dirName)
        val file1 = parent1.child(name1)
        val file2 = parent2.child(name2)
        file1.writeText(text1)
        file2.writeText(text2)
        assertEquals(text1, parent2.child(name1).readText())
        assertEquals(text2, parent1.child(name2).readText())
    }

    @Test
    fun lengthFile() {
        val line1 = "foo1"
        val line2 = "bar22"
        val file = dir.child("file")
        file.writeLines(listOf(line1))
        val length1 = file.length()
        file.writeLines(listOf(line2), true)
        assertEquals(listOf(line1, line2), file.readLines())
        assertEquals(listOf(line2), file.readLines(length1))
    }

    @Test
    fun deleteAndWriteWithReset() = deleteAndWrite(true)

    @Test
    fun deleteAndWriteWithoutReset() = deleteAndWrite(false)

    private fun deleteAndWrite(withReset: Boolean) {
        val line1 = "foo"
        val line2 = "bar"

        val file1 = dir.child("file")
        file1.writeLines(listOf(line1), true)
        assertEquals(listOf(line1), file1.readLines())

        file1.delete()
        if (withReset) dir.resetCache()

        val file2 = dir.child("file")
        file2.writeLines(listOf(line2), true)
        assertEquals(listOf(line2), file2.readLines())
    }

    @Test
    fun writeEmpty() {
        val line1 = "foo"
        val line2 = "bar"

        val file1 = dir.child("file")
        file1.writeLines(listOf(line1))
        assertEquals(listOf(line1), file1.readLines())
        file1.writeLines(emptyList())
        assertEquals(emptyList(), file1.readLines())
        assertTrue(file1.file.fileSystemNode is NonExistingNode)
        val file2 = dir.child("file")
        file2.writeLines(listOf(line2))
        assertEquals(listOf(line2), file2.readLines())
    }

    @Test
    fun listFilesRecursiveRelative() {
        val pathPred = { path: List<String> ->
            path == listOf("subPart", "exceptionNo") || !path.any { it.endsWith("No") }
        }
        val readBytesSrc = dir.child("readBytes")
        readBytesSrc.child("skipped").hiddenChild("decsync-sequence").writeText("1")
        readBytesSrc.child("tooLow").hiddenChild("decsync-sequence").writeText("2")

        val listDir = dir.child("list")
        val skipped = listDir.child("skipped")
        skipped.hiddenChild("decsync-sequence").writeText("1")
        skipped.child("baz", "foo").writeText("abc")
        skipped.child("bazNo", "bar").writeText("def")
        val tooLow = listDir.child("tooLow")
        tooLow.hiddenChild("decsync-sequence").writeText("3")
        tooLow.child("bar", "foo").writeText("cba")
        tooLow.child("barNo", "baz").writeText("def")
        val subPart = listDir.child("subPart")
        subPart.child("foo").hiddenChild("decsync-sequence").writeText("5")
        subPart.child("foo").hiddenChild("foo").writeText("1")
        subPart.child("foo").hiddenChild("bar").writeText("2")
        subPart.child("foo", ".bar").writeText("123")
        subPart.child("foo", "baz").writeText("456")
        subPart.child("foo", "fooNo").writeText("789")
        subPart.child("fooNo", "bar").writeText("012")
        subPart.child("exceptionNo").writeText("345")

        dir.resetCache()
        val result = mutableListOf<ArrayList<String>>()
        dir.child("list").listFilesRecursiveRelative(
                dir.child("readBytes"),
                pathPred
        ) { path ->
            result.add(path)
        }
        assertEquals(
                setOf(
                        listOf("tooLow", "bar", "foo"),
                        listOf("subPart", "foo", ".bar"),
                        listOf("subPart", "foo", "baz"),
                        listOf("subPart", "exceptionNo")
                ),
                result.toSet()
        )
        assertEquals(result.toSet().size, result.size)
        assertEquals("1", dir.child("readBytes", "skipped").hiddenChild("decsync-sequence").readText())
        assertEquals("3", dir.child("readBytes", "tooLow").hiddenChild("decsync-sequence").readText())
        assertEquals("5", dir.child("readBytes", "subPart", "foo").hiddenChild("decsync-sequence").readText())
    }

    @Test
    fun listFilesRecursiveRelativeDefaultParams() {
        val listDir = dir.child("list")
        val skipped = listDir.child("skipped")
        skipped.hiddenChild("decsync-sequence").writeText("1")
        skipped.child("baz", "foo").writeText("abc")
        skipped.child("bazNo", "bar").writeText("def")
        val tooLow = listDir.child("tooLow")
        tooLow.hiddenChild("decsync-sequence").writeText("3")
        tooLow.child("bar", "foo").writeText("cba")
        tooLow.child("barNo", "baz").writeText("def")
        val subPart = listDir.child("subPart")
        subPart.child("foo").hiddenChild("decsync-sequence").writeText("5")
        subPart.child("foo").hiddenChild("foo").writeText("1")
        subPart.child("foo").hiddenChild("bar").writeText("2")
        subPart.child("foo", ".bar").writeText("123")
        subPart.child("foo", "baz").writeText("456")
        subPart.child("foo", "fooNo").writeText("789")
        subPart.child("fooNo", "bar").writeText("012")
        subPart.child("exceptionNo").writeText("345")

        dir.resetCache()
        val result = mutableListOf<ArrayList<String>>()
        dir.child("list").listFilesRecursiveRelative { path ->
            result.add(path)
        }
        assertEquals(
                setOf(
                        listOf("skipped", "baz", "foo"),
                        listOf("skipped", "bazNo", "bar"),
                        listOf("tooLow", "bar", "foo"),
                        listOf("tooLow", "barNo", "baz"),
                        listOf("subPart", "foo", ".bar"),
                        listOf("subPart", "foo", "baz"),
                        listOf("subPart", "foo", "fooNo"),
                        listOf("subPart", "fooNo", "bar"),
                        listOf("subPart", "exceptionNo")
                ),
                result.toSet()
        )
        assertEquals(result.toSet().size, result.size)
    }

    @Test
    fun listDirectories() {
        dir.child("dir1", "foo").writeText("bar")
        dir.child(".dir2", "foo").writeText("bar")
        dir.hiddenChild("dir3").child("foo").writeText("bar")
        dir.child("file").writeText("bar")

        val result = dir.listDirectories()
        assertEquals(
                setOf("dir1", ".dir2"),
                result.toSet()
        )
        assertEquals(result.toSet().size, result.size)
    }
}