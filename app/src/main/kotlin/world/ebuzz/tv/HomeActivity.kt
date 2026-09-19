package world.ebuzz.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.link.DeviceLink
import world.ebuzz.tv.core.link.DevicePicker
import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.core.ui.KeyHandler
import world.ebuzz.tv.core.ui.applyOrientation
import world.ebuzz.tv.databinding.ActivityHomeBinding

/**
 * The shell: a tab per section and the current section's fragment. It knows nothing about what a section is —
 * [homeSections] is supplied by the edition's source set, so an edition only contains the features it lists.
 */
class HomeActivity : FragmentActivity() {
    private lateinit var b: ActivityHomeBinding
    private val sections: List<HomeSection> = homeSections
    private var current: HomeSection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        b = ActivityHomeBinding.inflate(layoutInflater).also { setContentView(it.root) }
        DeviceLink.install(application, linkKinds)
        b.btnDevices.visibility = if (DeviceLink.isTvDevice) View.VISIBLE else View.GONE      // a phone casts from its player; only a TV pulls
        b.btnDevices.setOnClickListener { DevicePicker.pull(this) }
        b.tabs.visibility = if (sections.size > 1 || DeviceLink.isTvDevice) View.VISIBLE else View.GONE
        if (sections.size > 1) sections.forEachIndexed { i, s ->
            b.tabs.addView((layoutInflater.inflate(R.layout.view_tab, b.tabs, false) as TextView).apply {
                text = s.title; tag = s.id; setOnClickListener { show(s) }
            }, i)
        }
        show(sections.firstOrNull { it.id == container.sections.last } ?: sections.first())
    }

    private fun show(section: HomeSection) {
        if (section == current) return
        current = section
        container.sections.last = section.id
        for (i in 0 until b.tabs.childCount) b.tabs.getChildAt(i).let { it.isSelected = it.tag == section.id }
        // fragments are kept per section so switching tabs preserves scroll position and loaded pages
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            supportFragmentManager.fragments.forEach { hide(it) }
            supportFragmentManager.findFragmentByTag(section.id)?.let { show(it) } ?: add(R.id.content, section.newFragment(), section.id)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val f = current?.let { supportFragmentManager.findFragmentByTag(it.id) }
        if ((f as? KeyHandler)?.onKey(keyCode, event) == true) return true
        return super.onKeyDown(keyCode, event)
    }
}
