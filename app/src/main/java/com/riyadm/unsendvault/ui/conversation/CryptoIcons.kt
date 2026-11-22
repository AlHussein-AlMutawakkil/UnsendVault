package com.riyadm.unsendvault.ui.conversation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object CryptoIcons {
    private var _removeModerator: ImageVector? = null
    private var _shieldLock: ImageVector? = null
    private var _verifiedUser: ImageVector? = null

    val RemoveModerator: ImageVector
        get() {
            if (_removeModerator != null) {
                return _removeModerator!!
            }
            _removeModerator = ImageVector.Builder(
                name = "remove_moderator",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f,
                    pathFillType = PathFillType.Companion.NonZero,
                ) {
                    moveTo(18.85f, 16.05f)
                    lineTo(17.35f, 14.5f)
                    quadToRelative(0.3f, -0.8f, 0.47f, -1.66f)
                    quadTo(18f, 11.98f, 18f, 11.1f)
                    verticalLineTo(6.38f)
                    lineTo(12f, 4.13f)
                    lineTo(8.35f, 5.5f)
                    lineTo(6.8f, 3.95f)
                    lineTo(12f, 2f)
                    lineToRelative(8f, 3f)
                    verticalLineToRelative(6.1f)
                    quadToRelative(0f, 1.28f, -0.29f, 2.53f)
                    reflectiveQuadToRelative(-0.86f, 2.43f)
                    close()
                    moveTo(19.8f, 22.6f)
                    lineTo(16.55f, 19.35f)
                    quadToRelative(-0.95f, 0.97f, -2.11f, 1.64f)
                    quadTo(13.28f, 21.65f, 12f, 22f)
                    quadTo(8.53f, 21.13f, 6.26f, 18.01f)
                    reflectiveQuadTo(4f, 11.1f)
                    verticalLineTo(6.8f)
                    lineTo(1.4f, 4.2f)
                    lineTo(2.8f, 2.8f)
                    lineTo(21.2f, 21.2f)
                    lineToRelative(-1.4f, 1.4f)
                    close()
                    moveTo(10.58f, 13.38f)
                    close()
                    moveTo(12.85f, 10f)
                    close()
                    moveTo(12f, 19.9f)
                    quadToRelative(0.88f, -0.27f, 1.68f, -0.77f)
                    reflectiveQuadToRelative(1.47f, -1.18f)
                    lineTo(6f, 8.8f)
                    verticalLineToRelative(2.3f)
                    quadToRelative(0f, 3.03f, 1.7f, 5.5f)
                    reflectiveQuadTo(12f, 19.9f)
                    close()
                }
            }.build()
            return _removeModerator!!
        }

    val ShieldLock: ImageVector
        get() {
            if (_shieldLock != null) {
                return _shieldLock!!
            }
            _shieldLock = ImageVector.Builder(
                name = "shield_lock",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f,
                    pathFillType = PathFillType.Companion.NonZero,
                ) {
                    moveTo(12f, 22f)
                    quadTo(8.53f, 21.13f, 6.26f, 18.01f)
                    reflectiveQuadTo(4f, 11.1f)
                    verticalLineTo(5f)
                    lineTo(12f, 2f)
                    lineToRelative(8f, 3f)
                    verticalLineToRelative(6.1f)
                    quadToRelative(0f, 3.8f, -2.26f, 6.91f)
                    reflectiveQuadTo(12f, 22f)
                    close()
                    moveToRelative(0f, -2.1f)
                    quadToRelative(2.6f, -0.82f, 4.3f, -3.3f)
                    reflectiveQuadTo(18f, 11.1f)
                    verticalLineTo(6.38f)
                    lineTo(12f, 4.13f)
                    lineTo(6f, 6.38f)
                    verticalLineTo(11.1f)
                    quadToRelative(0f, 3.03f, 1.7f, 5.5f)
                    reflectiveQuadTo(12f, 19.9f)
                    close()
                    moveTo(12f, 12f)
                    close()
                    moveToRelative(-2f, 4f)
                    horizontalLineToRelative(4f)
                    quadToRelative(0.43f, 0f, 0.71f, -0.29f)
                    reflectiveQuadTo(15f, 15f)
                    verticalLineTo(12f)
                    quadToRelative(0f, -0.43f, -0.29f, -0.71f)
                    reflectiveQuadTo(14f, 11f)
                    verticalLineTo(10f)
                    quadTo(14f, 9.17f, 13.41f, 8.59f)
                    reflectiveQuadTo(12f, 8f)
                    reflectiveQuadTo(10.59f, 8.59f)
                    reflectiveQuadTo(10f, 10f)
                    verticalLineToRelative(1f)
                    quadTo(9.58f, 11f, 9.29f, 11.29f)
                    reflectiveQuadTo(9f, 12f)
                    verticalLineToRelative(3f)
                    quadToRelative(0f, 0.42f, 0.29f, 0.71f)
                    quadTo(9.58f, 16f, 10f, 16f)
                    close()
                    moveToRelative(1f, -5f)
                    verticalLineTo(10f)
                    quadTo(11f, 9.57f, 11.29f, 9.29f)
                    reflectiveQuadTo(12f, 9f)
                    reflectiveQuadToRelative(0.71f, 0.29f)
                    reflectiveQuadTo(13f, 10f)
                    verticalLineToRelative(1f)
                    horizontalLineTo(11f)
                    close()
                }
            }.build()
            return _shieldLock!!
        }

    val VerifiedUser: ImageVector
        get() {
            if (_verifiedUser != null) {
                return _verifiedUser!!
            }
            _verifiedUser = ImageVector.Builder(
                name = "verified_user",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    fillAlpha = 1f,
                    stroke = null,
                    strokeAlpha = 1f,
                    strokeLineWidth = 1f,
                    strokeLineCap = StrokeCap.Butt,
                    strokeLineJoin = StrokeJoin.Bevel,
                    strokeLineMiter = 1f,
                    pathFillType = PathFillType.Companion.NonZero,
                ) {
                    moveTo(10.95f, 15.55f)
                    lineTo(16.6f, 9.9f)
                    lineTo(15.18f, 8.48f)
                    lineTo(10.95f, 12.7f)
                    lineTo(8.85f, 10.6f)
                    lineTo(7.43f, 12.02f)
                    lineToRelative(3.53f, 3.53f)
                    close()
                    moveTo(12f, 22f)
                    quadTo(8.53f, 21.13f, 6.26f, 18.01f)
                    reflectiveQuadTo(4f, 11.1f)
                    verticalLineTo(5f)
                    lineTo(12f, 2f)
                    lineToRelative(8f, 3f)
                    verticalLineToRelative(6.1f)
                    quadToRelative(0f, 3.8f, -2.26f, 6.91f)
                    reflectiveQuadTo(12f, 22f)
                    close()
                    moveToRelative(0f, -2.1f)
                    quadToRelative(2.6f, -0.82f, 4.3f, -3.3f)
                    reflectiveQuadTo(18f, 11.1f)
                    verticalLineTo(6.38f)
                    lineTo(12f, 4.13f)
                    lineTo(6f, 6.38f)
                    verticalLineTo(11.1f)
                    quadToRelative(0f, 3.03f, 1.7f, 5.5f)
                    reflectiveQuadTo(12f, 19.9f)
                    close()
                    moveTo(12f, 12f)
                    close()
                }
            }.build()
            return _verifiedUser!!
        }
}
