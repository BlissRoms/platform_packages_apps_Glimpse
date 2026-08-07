/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.navigationrail.NavigationRailView
import org.lineageos.glimpse.R
import org.lineageos.glimpse.SettingsActivity
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.AlbumType

class MainFragment : Fragment(R.layout.fragment_main) {
    // Views
    private val navigationBarView by getViewProperty<NavigationBarView>(R.id.navigationBarView)
    private val slidingActivePill by getViewProperty<View?>(R.id.slidingActivePill)
    private val settingsMaterialButton by getViewProperty<MaterialButton>(R.id.settingsMaterialButton)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)
    private val viewPager2 by getViewProperty<ViewPager2>(R.id.viewPager2)

    private var pageAnimator: ValueAnimator? = null

    private fun animateActivePillToPosition(position: Int) {
        navigationBarView.post {
            val isVertical = navigationBarView is NavigationRailView
            if (isVertical) {
                val menuItemId = navigationBarView.menu.getItem(position).itemId
                val itemView = navigationBarView.findViewById<View>(menuItemId)
                if (itemView != null) {
                    val parentView = itemView.parent as View
                    
                    // Center it perfectly over the icon
                    val itemCenterY = itemView.y + parentView.y + (itemView.height / 2f)
                    
                    val pillHeight = slidingActivePill?.height ?: 0
                    val targetY = itemCenterY - (pillHeight / 2f)
                    
                    slidingActivePill?.animate()
                        ?.translationY(targetY)
                        ?.setDuration(300)
                        ?.setInterpolator(android.view.animation.OvershootInterpolator(1.0f))
                        ?.start()
                }
            } else {
                val itemWidth = navigationBarView.width / 3f
                slidingActivePill?.animate()
                    ?.translationX(position * itemWidth)
                    ?.setDuration(300)
                    ?.setInterpolator(android.view.animation.OvershootInterpolator(1.0f))
                    ?.start()
            }
        }
    }

    private fun smoothScrollToPage(targetItem: Int) {
        if (viewPager2.currentItem == targetItem) return
        
        // Start pill animation immediately so it syncs with the page transition
        animateActivePillToPosition(targetItem)
        
        if (pageAnimator?.isRunning == true) {
            pageAnimator?.cancel()
        }
        
        val pxToDrag = viewPager2.width * (targetItem - viewPager2.currentItem)
        var previousValue = 0
        
        pageAnimator = ValueAnimator.ofInt(0, pxToDrag).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { animator ->
                val currentValue = animator.animatedValue as Int
                val diff = currentValue - previousValue
                viewPager2.fakeDragBy(-diff.toFloat())
                previousValue = currentValue
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (!viewPager2.isFakeDragging) {
                        viewPager2.beginFakeDrag()
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (viewPager2.isFakeDragging) {
                        viewPager2.endFakeDrag()
                    }
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    if (viewPager2.isFakeDragging) {
                        viewPager2.endFakeDrag()
                    }
                }
            })
            start()
        }
    }

    private val onPageChangeCallback by lazy {
        object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                navigationBarView.menu.getItem(position).isChecked = true

                // Only animate if we are NOT fake dragging (e.g., user swiped manually)
                if (pageAnimator?.isRunning != true) {
                    animateActivePillToPosition(position)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar
        toolbar.setupWithNavController(findNavController())

        settingsMaterialButton.setOnClickListener {
            val intent = Intent(context, SettingsActivity::class.java)
            startActivity(intent)
        }

        // ViewPager2
        viewPager2.isUserInputEnabled = false
        viewPager2.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int) = fragments[position]()
        }
        viewPager2.offscreenPageLimit = fragments.size
        viewPager2.registerOnPageChangeCallback(onPageChangeCallback)

        // Prevent BottomNavigationView from automatically adding system navigation bar padding
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navigationBarView) { _, windowInsets ->
            windowInsets
        }

        navigationBarView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.reelsFragment -> {
                    smoothScrollToPage(0)
                    true
                }

                R.id.albumsFragment -> {
                    smoothScrollToPage(1)
                    true
                }

                R.id.libraryFragment -> {
                    smoothScrollToPage(2)
                    true
                }

                else -> false
            }
        }
    }

    override fun onDestroyView() {
        // ViewPager2
        viewPager2.unregisterOnPageChangeCallback(onPageChangeCallback)
        viewPager2.adapter = null

        super.onDestroyView()
    }

    companion object {
        // Keep in sync with the NavigationBarView menu
        private val fragments = arrayOf(
            {
                AlbumFragment().apply {
                    arguments = AlbumFragment.createBundle(
                        albumType = AlbumType.REELS,
                        hideToolbar = true,
                    )
                }
            },
            { AlbumsFragment() },
            { LibraryFragment() },
        )
    }
}
