package io.github.thirtyeighttwentysix.volan.idea

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class VolanIconsTest : BasePlatformTestCase() {
    fun testFileAndPluginLogosLoadAtTheirNativeSizes() {
        assertSame(VolanIcons.FILE, VolanFileType.INSTANCE.icon)
        val variants = listOf("/icons/volan.svg", "/icons/volan_dark.svg", "/META-INF/pluginIcon.svg", "/META-INF/pluginIcon_dark.svg")
        val preview = BufferedImage(480, 180, BufferedImage.TYPE_INT_ARGB)
        val graphics = preview.createGraphics()
        graphics.color = Color.WHITE; graphics.fillRect(0, 0, 240, 180)
        graphics.color = Color(30, 31, 34); graphics.fillRect(240, 0, 240, 180)
        variants.forEach { path ->
            val icon = IconLoader.getIcon(path, VolanIcons::class.java)
            val size = if (path.startsWith("/icons/")) 16 else 40
            assertEquals(path, size, icon.iconWidth)
            assertEquals(path, size, icon.iconHeight)
            val rendered = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = rendered.createGraphics(); icon.paintIcon(null, g, 0, 0); g.dispose()
            val visiblePixels = (0 until size).sumOf { x -> (0 until size).count { y -> rendered.getRGB(x, y) ushr 24 > 0 } }
            assertTrue("Logo must render actual pixels: $path", visiblePixels > size)
            val dark = path.contains("_dark")
            val x = if (dark) 280 else 40
            val y = if (size == 16) 112 else 28
            icon.paintIcon(null, graphics, x, y)
            // Enlarged preview uses the exact icon rendered by IntelliJ, not a second SVG renderer.
            graphics.drawImage(rendered, x + 60, y, size * 2, size * 2, null)
        }
        graphics.dispose()
        val output = File("build/icon-preview.png"); output.parentFile.mkdirs()
        ImageIO.write(preview, "png", output)
    }
}
