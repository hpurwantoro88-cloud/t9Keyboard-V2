package com.opent9.keyboard

import android.view.MotionEvent
import android.view.inputmethod.InputConnection
import com.opent9.keyboard.ui.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmojiPickerPageTest {

    @Test
    fun testEmojiAtlasHitTestingAndRecents() {
        val emojiAtlas = EmojiAtlas()
        emojiAtlas.computeLayout(1080f, 780f, 3f)

        // 1. Category tabs: 2 tabs across 1080px (each tab is 540px wide, 0..120px tall)
        assertEquals(0, emojiAtlas.findCategoryTabAt(200f, 50f)) // Tab 0: Smileys
        assertEquals(1, emojiAtlas.findCategoryTabAt(800f, 50f)) // Tab 1: Memoji
        assertNull(emojiAtlas.findCategoryTabAt(50f, 150f)) // Below tabs

        // 2. Emoji grid: starts at tabHeight (120px), 4 rows x 7 cols
        // Grid height: 540px. Range: 120px .. 660px.
        // Col width: 1080 / 7 = 154.28px. Row height: 135px.
        assertEquals(0, emojiAtlas.findEmojiIndexAt(50f, 150f, scrollY = 0f)) // Row 0, Col 0
        assertEquals(1, emojiAtlas.findEmojiIndexAt(200f, 150f, scrollY = 0f)) // Row 0, Col 1
        assertEquals(7, emojiAtlas.findEmojiIndexAt(50f, 300f, scrollY = 0f)) // Row 1, Col 0
        assertNull(emojiAtlas.findEmojiIndexAt(50f, 50f, scrollY = 0f)) // Above grid

        // 2b. Scroll hit-testing: scroll up by 1 row (-135px)
        // Position y=150f now maps to Row 1!
        assertEquals(7, emojiAtlas.findEmojiIndexAt(50f, 150f, scrollY = -135f))

        // 3. Control row: starts at 660px, 4 buttons (each 270px wide)
        assertEquals(0, emojiAtlas.findControlIndexAt(100f, 700f)) // ABC
        assertEquals(1, emojiAtlas.findControlIndexAt(350f, 700f)) // Recents
        assertEquals(2, emojiAtlas.findControlIndexAt(600f, 700f)) // Space
        assertEquals(3, emojiAtlas.findControlIndexAt(900f, 700f)) // DEL
        assertNull(emojiAtlas.findControlIndexAt(100f, 500f)) // Above control row

        // 4. Recents recording
        val initialRecent = emojiAtlas.recentEmojis[0]
        val testEmoji = "🔥"
        assertNotEquals(initialRecent, testEmoji)
        emojiAtlas.recordRecentEmoji(testEmoji)
        assertEquals(testEmoji, emojiAtlas.recentEmojis[0])

        // Re-recording moves to front without duplicating
        val secondEmoji = "🚀"
        emojiAtlas.recordRecentEmoji(secondEmoji)
        assertEquals(secondEmoji, emojiAtlas.recentEmojis[0])
        assertEquals(testEmoji, emojiAtlas.recentEmojis[1])
    }

    @Test
    fun testKeyAtlasPage3Configuration() {
        val keyAtlas = KeyAtlas()
        keyAtlas.computeGeometry(1080f, 780f, 3f)

        // Switch to Page 3
        keyAtlas.updatePageLayout(KeyboardPage.PAGE_3_EMOJI)
        assertEquals(KeyboardPage.PAGE_3_EMOJI, keyAtlas.currentPage)

        // findKeyAt returns null for Page 3 because it has dedicated layout
        assertNull(keyAtlas.findKeyAt(100f, 200f))
        assertNull(keyAtlas.findKeyAt(500f, 500f))

        // Key 12 in keys array is configured as ABC -> PAGE_0_TEXT
        val key12 = keyAtlas.keys[12]
        assertEquals(KeyType.PAGE_SWITCH, key12.type)
        assertEquals("ABC", key12.primaryLabel)
    }

    @Test
    fun testTouchGestureTrackerPage3Routing() {
        val keyAtlas = KeyAtlas()
        keyAtlas.computeGeometry(1080f, 780f, 3f)
        keyAtlas.updatePageLayout(KeyboardPage.PAGE_3_EMOJI)

        var tappedCategory: Int? = null
        var tappedEmoji: String? = null
        var tappedControl: Int? = null
        var swipedDirection: FlickDirection? = null

        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
                fail("Standard onKeyTap should not be called on Page 3!")
            }
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {
                fail("Candidate strip onPillTap should not be called on Page 3!")
            }
            override fun onCandidateTap(index: Int) {
                fail("Candidate strip onCandidateTap should not be called on Page 3!")
            }
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}

            override fun onEmojiCategoryTap(categoryIndex: Int) {
                tappedCategory = categoryIndex
            }
            override fun onEmojiTap(emoji: String) {
                tappedEmoji = emoji
            }
            override fun onEmojiControlTap(controlIndex: Int) {
                tappedControl = controlIndex
            }
            override fun onEmojiPageSwipe(direction: FlickDirection) {
                swipedDirection = direction
            }
        }

        val tracker = TouchGestureTracker(keyAtlas, listener)

        // 1. Tap on Category Tab 1 (x=800, y=50 -> Tab 1: Memoji)
        val t0 = 1000L
        val downTab = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 800f, 50f, 0)
        tracker.onTouchEvent(downTab) { null }
        val upTab = MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_UP, 800f, 50f, 0)
        tracker.onTouchEvent(upTab) { null }
        assertEquals(1, tappedCategory)

        // 2. Tap on Emoji in Grid (Row 0, Col 0: x=50, y=150)
        val downEmoji = MotionEvent.obtain(t0 + 100, t0 + 100, MotionEvent.ACTION_DOWN, 50f, 150f, 0)
        tracker.onTouchEvent(downEmoji) { null }
        val upEmoji = MotionEvent.obtain(t0 + 100, t0 + 150, MotionEvent.ACTION_UP, 50f, 150f, 0)
        tracker.onTouchEvent(upEmoji) { null }
        assertNotNull(tappedEmoji)
        assertTrue(tappedEmoji!!.isNotEmpty())

        // 3. Tap on "ABC" control button (x=100, y=700)
        val downAbc = MotionEvent.obtain(t0 + 200, t0 + 200, MotionEvent.ACTION_DOWN, 100f, 700f, 0)
        tracker.onTouchEvent(downAbc) { null }
        val upAbc = MotionEvent.obtain(t0 + 200, t0 + 250, MotionEvent.ACTION_UP, 100f, 700f, 0)
        tracker.onTouchEvent(upAbc) { null }
        assertEquals(0, tappedControl) // 0 is ABC

        // 4. Swipe Left on emoji grid to cycle category
        val downSwipe = MotionEvent.obtain(t0 + 300, t0 + 300, MotionEvent.ACTION_DOWN, 500f, 300f, 0)
        tracker.onTouchEvent(downSwipe) { null }
        val moveSwipe = MotionEvent.obtain(t0 + 300, t0 + 350, MotionEvent.ACTION_MOVE, 350f, 300f, 0) // -150px left
        tracker.onTouchEvent(moveSwipe) { null }
        val upSwipe = MotionEvent.obtain(t0 + 300, t0 + 400, MotionEvent.ACTION_UP, 350f, 300f, 0)
        tracker.onTouchEvent(upSwipe) { null }
        assertEquals(FlickDirection.LEFT, swipedDirection)

        // 5. Vertical drag scrolling
        val downScroll = MotionEvent.obtain(t0 + 500, t0 + 500, MotionEvent.ACTION_DOWN, 500f, 400f, 0)
        tracker.onTouchEvent(downScroll) { null }
        val moveScroll = MotionEvent.obtain(t0 + 500, t0 + 550, MotionEvent.ACTION_MOVE, 500f, 300f, 0) // -100px up
        tracker.onTouchEvent(moveScroll) { null }
        assertTrue("Emoji vertical scroll offset should be negative when dragging up", tracker.emojiScrollOffset < 0f)
        val upScroll = MotionEvent.obtain(t0 + 500, t0 + 600, MotionEvent.ACTION_UP, 500f, 300f, 0)
        tracker.onTouchEvent(upScroll) { null }
    }

    @Test
    fun testAbcShortcutReturnsToPage0NotPage1() {
        val keyAtlas = KeyAtlas()
        keyAtlas.computeGeometry(1080f, 780f, 3f)

        // Start on Page 0, switch to Page 3
        keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
        assertEquals(KeyboardPage.PAGE_0_TEXT, keyAtlas.currentPage)

        keyAtlas.updatePageLayout(KeyboardPage.PAGE_3_EMOJI)
        assertEquals(KeyboardPage.PAGE_3_EMOJI, keyAtlas.currentPage)

        var controlTapped: Int? = null
        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {}
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}
            override fun onEmojiControlTap(controlIndex: Int) {
                controlTapped = controlIndex
                if (controlIndex == 0) {
                    keyAtlas.updatePageLayout(KeyboardPage.PAGE_0_TEXT)
                }
            }
        }

        val tracker = TouchGestureTracker(keyAtlas, listener)

        // Tap ABC at bottom-left
        val t0 = 1000L
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 50f, 700f, 0)
        tracker.onTouchEvent(down) { null }
        val up = MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_UP, 50f, 700f, 0)
        tracker.onTouchEvent(up) { null }

        assertEquals(0, controlTapped)
        // VERIFY: Page is PAGE_0_TEXT (Page 0) and NEVER PAGE_1_NUM_SYM (Page 1)
        assertEquals(KeyboardPage.PAGE_0_TEXT, keyAtlas.currentPage)
        assertNotEquals(KeyboardPage.PAGE_1_NUM_SYM, keyAtlas.currentPage)
    }

    @Test
    fun testEmojiSelectionCommitsEmojiNotCharacters() {
        val ic = mockk<InputConnection>(relaxed = true)
        val keyAtlas = KeyAtlas()
        keyAtlas.computeGeometry(1080f, 780f, 3f)
        keyAtlas.updatePageLayout(KeyboardPage.PAGE_3_EMOJI)

        var committedText: String? = null
        val listener = object : TouchGestureListener {
            override fun onKeyTap(key: KeyInfo, touchX: Float, touchY: Float) {
                fail("Emoticon selection should not trigger onKeyTap which inserts alphanumeric characters!")
            }
            override fun onKeyFlick(key: KeyInfo, direction: FlickDirection) {}
            override fun onKeyLongPress(key: KeyInfo) {}
            override fun onSpaceScrub(steps: Int) {}
            override fun onPillTap() {}
            override fun onCandidateTap(index: Int) {}
            override fun onStripScroll(newScrollOffset: Float) {}
            override fun onTouchStateChanged(activeKeyId: Int?) {}

            override fun onEmojiTap(emoji: String) {
                committedText = emoji
                ic.commitText(emoji, 1)
            }
        }

        val tracker = TouchGestureTracker(keyAtlas, listener)

        // Tap emoji in grid
        val t0 = 1000L
        val down = MotionEvent.obtain(t0, t0, MotionEvent.ACTION_DOWN, 50f, 150f, 0)
        tracker.onTouchEvent(down) { null }
        val up = MotionEvent.obtain(t0, t0 + 50, MotionEvent.ACTION_UP, 50f, 150f, 0)
        tracker.onTouchEvent(up) { null }

        assertNotNull(committedText)
        verify { ic.commitText(committedText!!, 1) }
    }
}
