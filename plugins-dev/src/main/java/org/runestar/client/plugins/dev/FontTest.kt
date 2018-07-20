package org.runestar.client.plugins.dev

import org.runestar.client.api.util.DisposablePlugin
import org.runestar.client.game.api.Fonts
import org.runestar.client.game.api.live.LiveCanvas
import org.runestar.client.plugins.spi.PluginSettings
import java.awt.Color

class FontTest : DisposablePlugin<PluginSettings>() {

    override val defaultSettings = PluginSettings()

    private val fonts = listOf(
            Fonts.CHAT_BOLD,
            Fonts.CHAT,
            Fonts.LARGE,
            Fonts.SMALL,
            Fonts.NPC_CHAT
    )

    val s = "1234567890 abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOPQRSTUVWXYZ_/:><"

    override fun start() {
        add(LiveCanvas.repaints.subscribe { g ->
            g.color = Color.WHITE
            val x = 20
            var y = 20

            for (f in fonts) {
                g.font = f
                g.drawString(s, x, y)
                y += 30
            }
        })
    }
}