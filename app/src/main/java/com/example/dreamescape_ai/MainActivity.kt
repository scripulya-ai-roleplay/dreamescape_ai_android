package com.example.dreamescape_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dreamescape_ai.model.SampleData
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Dreamescape_aiTheme {
                ScripulyaApp(
                    profile = SampleData.profile,
                    history = SampleData.history,
                    onChangeCharacter = {
                        startActivity(Intent(this, CreateCharacterActivity::class.java))
                    },
                    onChatClick = { chat ->
                        // Tapping a conversation opens it in the chat activity.
                        chat.id?.let { chatId ->
                            startActivity(
                                Intent(this, ChatActivity::class.java).apply {
                                    putExtra(ChatActivity.EXTRA_CHAT_ID, chatId.toString())
                                    putExtra(ChatActivity.EXTRA_CHAT_TITLE, chat.title)
                                }
                            )
                        }
                    },
                    onStoryClick = { story ->
                        // A story is a scene; tapping it opens the scene preview.
                        startActivity(
                            Intent(this, ScenePreviewActivity::class.java).apply {
                                putExtra(ScenePreviewActivity.EXTRA_SCENE_ID, story.id)
                            }
                        )
                    },
                    onCreateCharacter = {
                        startActivity(Intent(this, CreateCharacterActivity::class.java))
                    },
                    onCreateScene = {
                        startActivity(Intent(this, CreateSceneActivity::class.java))
                    },
                    onMyCharacters = {
                        startActivity(Intent(this, MyCharactersActivity::class.java))
                    },
                    onMyScenes = {
                        startActivity(Intent(this, MyScenesActivity::class.java))
                    }
                    // onPlay left as a no-op: the History feed is still sample data.
                )
            }
        }
    }
}
