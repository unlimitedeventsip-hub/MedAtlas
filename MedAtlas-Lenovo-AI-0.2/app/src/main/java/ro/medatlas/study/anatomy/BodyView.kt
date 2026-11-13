package ro.medatlas.study.anatomy

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private data class Layer(val id: Int, val title: String, val size: Int)
private val colors = listOf(0xD7AB96,0xDFD0B4,0xE6BE68,0xB75864,0xC991A4,0xBC8970,0xAF727A,0xCB9CA5,0xCBA368,0xB46E68,0x94A7AA)

@Composable internal fun OfflineBody() {
    val context = LocalContext.current
    val layers = remember { context.assets.open("body/layers.tsv").bufferedReader().useLines { rows ->
        rows.map { val f = it.split('\t'); Layer(f[0].toInt(),f[1],f[3].toInt()) }.toList()
    } }
    var visible by remember { mutableStateOf(layers.filter { it.id in setOf(1,3,4,5) }.map { it.id }.toSet()) }
    val body = remember { BodySurface(context,layers) }
    DisposableEffect(body) { onDispose { body.onPause() } }
    Column(Modifier.fillMaxSize().padding(8.dp)) {
        Text("Glisează pentru rotire · apropie două degete pentru zoom")
        // User-facing and accessibility state must not depend on the GPU rendering
        // loop. Android emulators and power-save devices may delay OpenGL callbacks.
        Text("Corp 3D · ${visible.size} straturi vizibile", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            layers.forEach { layer ->
                FilterChip(selected = layer.id in visible, onClick = {
                    visible = if (layer.id in visible) visible - layer.id else visible + layer.id
                }, label = { Text(layer.title) })
            }
        }
        AndroidView(factory = { body }, update = { it.layers(visible) }, modifier = Modifier.fillMaxWidth().weight(1f))
        Text("BodyParts3D © The Database Center for Life Science · CC BY 4.0 · model 4.0 adaptat")
    }
}

private class BodySurface(context: Context, private val metadata: List<Layer>) : GLSurfaceView(context), GLSurfaceView.Renderer {
    private val buffers = mutableMapOf<Int,ByteBuffer>()
    private var shown: Set<Int> = emptySet()
    private var yaw = 0f
    private var pitch = 0f
    private var zoom = 1f
    private var prevX = 0f
    private var prevY = 0f
    private var aspect = 1f
    private val pinch = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val ratio = detector.scaleFactor
            queueEvent { zoom = (zoom * ratio).coerceIn(.6f,8f) }
            requestRender()
            return true
        }
    })
    init {
        setEGLConfigChooser(8,8,8,0,16,0)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Corp anatomic tridimensional cu straturi selectabile"
    }
    fun layers(ids: Set<Int>) {
        contentDescription = "Corp 3D · ${ids.size} straturi vizibile"
        queueEvent { shown = ids }
        requestRender()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        pinch.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_MOVE && e.pointerCount == 1) {
            val dx = (e.x - prevX) * .35f
            val dy = (e.y - prevY) * .35f
            queueEvent { yaw += dx; pitch = (pitch+dy).coerceIn(-88f,88f) }
            requestRender()
        }
        prevX=e.x; prevY=e.y
        return true
    }
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        gl.glClearColor(.96f,.95f,.93f,1f)
        gl.glEnable(GL10.GL_DEPTH_TEST)
        gl.glEnable(GL10.GL_LIGHTING)
        gl.glEnable(GL10.GL_LIGHT0)
        gl.glEnable(GL10.GL_COLOR_MATERIAL)
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
        gl.glEnableClientState(GL10.GL_NORMAL_ARRAY)
        gl.glLightfv(GL10.GL_LIGHT0,GL10.GL_POSITION,floatArrayOf(-.5f,.7f,1f,0f),0)
    }
    override fun onSurfaceChanged(gl: GL10,w:Int,h:Int) {
        aspect=w.toFloat()/h.coerceAtLeast(1)
        gl.glViewport(0,0,w,h)
    }
    override fun onDrawFrame(gl: GL10) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
        gl.glMatrixMode(GL10.GL_PROJECTION); gl.glLoadIdentity()
        val half = 7700f / zoom
        gl.glOrthof(-half*aspect,half*aspect,-half,half,-22000f,22000f)
        gl.glMatrixMode(GL10.GL_MODELVIEW); gl.glLoadIdentity()
        gl.glRotatef(pitch,1f,0f,0f); gl.glRotatef(yaw,0f,1f,0f)
        gl.glTranslatef(0f,-6700f,-900f)
        metadata.filter { it.id in shown }.forEach { layer ->
            val buffer = buffers.getOrPut(layer.id) {
                val direct = ByteBuffer.allocateDirect(layer.size).order(ByteOrder.nativeOrder())
                GZIPInputStream(context.assets.open("body/layer${layer.id}.mesh")).use { stream ->
                    val chunk = ByteArray(65536)
                    while (true) { val n = stream.read(chunk); if (n < 0) break; direct.put(chunk,0,n) }
                }
                direct.position(0)
                direct
            }
            val rgb = colors[layer.id]
            gl.glColor4f((rgb shr 16)/255f,((rgb shr 8) and 255)/255f,(rgb and 255)/255f,1f)
            buffer.position(0); gl.glVertexPointer(3,GL10.GL_SHORT,10,buffer)
            buffer.position(6); gl.glNormalPointer(GL10.GL_BYTE,10,buffer)
            gl.glDrawArrays(GL10.GL_TRIANGLES,0,layer.size/10)
        }
    }
}
