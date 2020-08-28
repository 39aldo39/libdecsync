package org.decsync.library

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@ExperimentalStdlibApi
class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_test)

        val directoryButton = findViewById<Button>(R.id.test_directory_button)
        directoryButton.setOnClickListener {
            DecsyncPrefUtils.chooseDecsyncDir(this)
        }

        val runButton = findViewById<Button>(R.id.test_run_button)
        runButton.setOnClickListener {
            TestsTask(this).execute()
        }

        val decsyncDir = DecsyncPrefUtils.getDecsyncDir(this)
        if (decsyncDir != null) {
            val name = DecsyncPrefUtils.getNameFromUri(this, decsyncDir)
            directoryButton.text = name
            runButton.isEnabled = true
        }
    }

    class TestsTask(
            private val mContext: Context
    ) : AsyncTask<Void, Void, Unit>() {
        override fun onPreExecute() {
            Toast.makeText(mContext, "Running tests!", Toast.LENGTH_SHORT).show()
        }

        override fun doInBackground(vararg params: Void) {
            val decsyncDir = DecsyncPrefUtils.getDecsyncDir(mContext)!!
            val nativeFile = nativeFileFromDirUri(mContext, decsyncDir)

            class NativeFileSafTest : NativeFileTest(nativeFile)
            runTests(NativeFileSafTest::class.java, NativeFileSafTest())

            class DecsyncFileSafTest : DecsyncFileTest(nativeFile)
            runTests(DecsyncFileSafTest::class.java, DecsyncFileSafTest())

            class DecsyncSafTest : DecsyncTest({ nativeFileFromDirUri(mContext, decsyncDir) }, null)
            runTests(DecsyncSafTest::class.java, DecsyncSafTest())
            class DecsyncSafTestV1 : DecsyncTest({ nativeFileFromDirUri(mContext, decsyncDir) }, DecsyncVersion.V1)
            runTests(DecsyncSafTestV1::class.java, DecsyncSafTestV1())
            class DecsyncSafTestV2 : DecsyncTest({ nativeFileFromDirUri(mContext, decsyncDir) }, DecsyncVersion.V2)
            runTests(DecsyncSafTestV2::class.java, DecsyncSafTestV2())
        }

        override fun onPostExecute(result: Unit) {
            Toast.makeText(mContext, "Tests done!", Toast.LENGTH_SHORT).show()
        }

        private fun <T> runTests(clazz: Class<T>, instance: T) {
            val methods = clazz.methods
            val testMethods = methods.filter {
                it.isAnnotationPresent(Test::class.java)
            }
            val beforeTestmethods = methods.filter {
                it.isAnnotationPresent(BeforeTest::class.java)
            }
            val afterTestmethods = methods.filter {
                it.isAnnotationPresent(AfterTest::class.java)
            }
            var success = true
            testMethods.forEach { method ->
                beforeTestmethods.forEach { it.invoke(instance) }
                try {
                    method.invoke(instance)
                } catch (e: Exception) {
                    success = false
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    e.printStackTrace(pw)
                    val stackTrace = sw.toString()
                    Log.e("Test failed!\n$stackTrace")
                }
                afterTestmethods.forEach { it.invoke(instance) }
            }
            if (success) {
                Log.i("Ran ${testMethods.size} tests of ${clazz.simpleName} successful!")
            } else {
                Log.w("Some tests of ${clazz.simpleName} failed!")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        DecsyncPrefUtils.chooseDecsyncDirResult(this, requestCode, resultCode, data) { uri ->
            val directoryButton = findViewById<Button>(R.id.test_directory_button)
            val name = DecsyncPrefUtils.getNameFromUri(this, uri)
            directoryButton.text = name

            val runButton = findViewById<Button>(R.id.test_run_button)
            runButton.isEnabled = true
        }
    }
}