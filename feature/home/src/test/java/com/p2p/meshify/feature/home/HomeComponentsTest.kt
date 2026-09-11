package com.p2p.meshify.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class HomeComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `ExpressiveChatSectionHeader renders`() {
        rule.setContent { DxTestTheme2 { ExpressiveChatSectionHeader(title="Recent Chats") } }
        rule.onNodeWithText("Recent Chats").assertIsDisplayed()
    }

    @Test fun `ExpressiveChatItem shows peer name and message`() {
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName="Ahmed", lastMessage="Hello", timestamp=System.currentTimeMillis(), unreadCount=0, isOnline=true, avatarHash=null, onClick={})
            }
        }
        rule.onNodeWithText("Ahmed").assertIsDisplayed()
        rule.onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test fun `unread badge shows and clamps at 99+`() {
        rule.setContent {
            DxTestTheme2 {
                Column {
                    ExpressiveChatItem(peerName="Sara", lastMessage=null, timestamp=0, unreadCount=5, isOnline=false, avatarHash=null, onClick={})
                    ExpressiveChatItem(peerName="Omar", lastMessage=null, timestamp=0, unreadCount=150, isOnline=false, avatarHash=null, onClick={})
                }
            }
        }
        rule.onNodeWithText("5").assertIsDisplayed()
        rule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test fun `unread badge hidden when zero`() {
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName="Fatima", lastMessage=null, timestamp=0, unreadCount=0, isOnline=false, avatarHash=null, onClick={})
            }
        }
        rule.onNodeWithText("0").assertDoesNotExist()
        rule.onNodeWithText("Fatima").assertIsDisplayed()
    }

    @Test fun `click invokes onClick`() {
        var clicked=false
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName="Khaled", lastMessage="Hi", timestamp=0, unreadCount=0, isOnline=false, avatarHash=null, onClick={clicked=true})
            }
        }
        rule.onNodeWithText("Khaled").performClick()
        assert(clicked)
    }

    @Test fun `online dot variation no crash`() {
        rule.setContent {
            DxTestTheme2 {
                Column {
                    ExpressiveChatItem(peerName="Nour", lastMessage=null, timestamp=0, unreadCount=0, isOnline=true, avatarHash=null, onClick={})
                    ExpressiveChatItem(peerName="Nour2", lastMessage=null, timestamp=0, unreadCount=0, isOnline=false, avatarHash=null, onClick={})
                }
            }
        }
        rule.onNodeWithText("Nour").assertIsDisplayed()
    }

    @Test fun `long peer name ellipsizes but still displayed`() {
        val longName = "A".repeat(50)
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName=longName, lastMessage="x", timestamp=0, unreadCount=1, isOnline=false, avatarHash=null, onClick={})
            }
        }
        rule.onNodeWithText(longName).assertIsDisplayed()
    }

    @Test fun `RTL header still displayed`() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxTestTheme2 { ExpressiveChatSectionHeader(title="Recent Chats") }
            }
        }
        rule.onNodeWithText("Recent Chats").assertIsDisplayed()
    }

    @Test fun `empty lastMessage no crash`() {
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName="Zero", lastMessage=null, timestamp=0, unreadCount=0, isOnline=false, avatarHash=null, onClick={})
            }
        }
        rule.onNodeWithText("Zero").assertIsDisplayed()
    }

    @Test fun `many items no crash - 60 rows`() {
        rule.setContent {
            DxTestTheme2 {
                Column {
                    repeat(60) { idx ->
                        ExpressiveChatItem(peerName="Peer $idx", lastMessage="msg $idx", timestamp=0, unreadCount=idx%5, isOnline=idx%2==0, avatarHash=null, onClick={})
                    }
                }
            }
        }
        rule.onNodeWithText("Peer 0").assertIsDisplayed()
    }

    @Test fun `timestamp formats not empty - uses util`() {
        rule.setContent {
            DxTestTheme2 {
                ExpressiveChatItem(peerName="Time", lastMessage="hey", timestamp= System.currentTimeMillis(), unreadCount=0, isOnline=false, avatarHash=null, onClick={})
            }
        }
        // just ensure not crash and name displayed
        rule.onNodeWithText("Time").assertIsDisplayed()
    }
}
