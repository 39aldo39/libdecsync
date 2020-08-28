package org.decsync.library

// This instantiates NativeFile with just an in-memory mapping of the file system

class MockRealFile(
        name: String,
        var content: ByteArray,
        val parent: MockRealDirectory
) : RealFile(name) {
    override fun delete() {
        parent.children.remove(this)
    }
    override fun length(): Int = content.size
    override fun read(readBytes: Int): ByteArray = content.copyOfRange(readBytes, content.size)
    override fun write(text: ByteArray, append: Boolean) {
        if (append) {
            content += text
        } else {
            content = text
        }
    }
}

class MockRealDirectory(
        name: String,
        val children: MutableList<RealNode>,
        val parent: MockRealDirectory?
) : RealDirectory(name) {
    override fun listChildren(): List<RealNode> = children
    override fun delete() {
        parent?.children?.remove(this)
    }
    override fun mkfile(name: String, text: ByteArray): RealFile {
        val file = MockRealFile(name, text, this)
        children += file
        return file
    }
    override fun mkdir(name: String): RealDirectory {
        val dir = MockRealDirectory(name, mutableListOf(), this)
        children += dir
        return dir
    }
}