package com.p2p.meshify.core.ui.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * MD3E corner-radius scale — follows Koda's house values.
 *
 * | Token | Value | Use |
 * |---|---|---|
 * | extraSmall | 8dp | Chips, small controls, thumbnails |
 * | small | 12dp | Icon boxes inside rows |
 * | medium | 20dp | Standard content card surface |
 * | large | 28dp | Pills — mini players, nav bar, hero buttons |
 * | extraLarge | 36dp | Large feature cards, sheets |
 * | dialog | 32dp | AlertDialog containers |
 * | full | 50% | FABs, play buttons, artwork accents |
 */
object DxShape {

    /* Raw scale */
    val ExtraSmall = RoundedCornerShape(8.dp)
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(20.dp)
    val Large = RoundedCornerShape(28.dp)
    val ExtraLarge = RoundedCornerShape(36.dp)

    /* Semantic aliases */
    val Chip = ExtraSmall
    val IconBox = Small
    val Card = Medium
    val Pill = Large
    val Sheet = ExtraLarge
    val Dialog = RoundedCornerShape(32.dp)
    val Full = RoundedCornerShape(50)
}
