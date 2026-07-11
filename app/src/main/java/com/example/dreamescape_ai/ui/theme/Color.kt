package com.example.dreamescape_ai.ui.theme

import androidx.compose.ui.graphics.Color

// --- Starry-night surfaces (deep blues / purples) ---
val NightVoid = Color(0xFF060A1C)   // app background, near-black indigo
val NightDeep = Color(0xFF0C1230)   // primary surface
val NightPanel = Color(0xFF141B3C)   // raised surface / cards
val NightPanelHi = Color(0xFF1E2750) // hovered/elevated surface
val NightOutline = Color(0xFF2A3360)

// --- Green background (overrides the default starry-night bg) ---
val GreenVoid = Color(0xFF0E3B22)   // green app background, gradient edges
val GreenDeep = Color(0xFF2E7D32)   // green gradient midpoint

// --- Brand gems ---
val ManaBlue = Color(0xFF39D3FF)     // Mana Credits (blue gem)
val ManaBlueDeep = Color(0xFF1B6FB8)
val ArcanePurple = Color(0xFFB06BFF) // Arcane/Elite Credits (purple gem)
val ArcanePurpleDeep = Color(0xFF6E35B8)

// --- Accents ---
val ScripulyaPink = Color(0xFFFF5FAE)
val ScripulyaGold = Color(0xFFFFC857)
val ScripulyaText = Color(0xFFECEBFF)
val ScripulyaTextDim = Color(0xFF9AA0C8)

// --- History filter button colors (Screen 2) ---
val FilterSaved = Color(0xFFFFC857)   // Saved - yellow
val FilterLikes = Color(0xFFFF5FAE)   // Likes - pink
val FilterComments = ManaBlue         // Comments - blue
val FilterFollowing = ArcanePurple    // Following - purple

// --- Content tag colors (Story cards) ---
val TagMature = Color(0xFFFF4D6D)     // 18+
val TagM = Color(0xFFFF8A3D)          // M
val TagMale = ManaBlue
val TagFemale = ScripulyaPink

// Kept for backward compatibility with the original template palette.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
