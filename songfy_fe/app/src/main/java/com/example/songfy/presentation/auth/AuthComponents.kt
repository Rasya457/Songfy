package com.lagu.songfy.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagu.songfy.presentation.theme.AccentSilver
import com.lagu.songfy.presentation.theme.SurfacePillInactive
import com.lagu.songfy.presentation.theme.TextPrimaryWhite
import com.lagu.songfy.presentation.theme.TextSecondaryGray

@Composable
fun CustomInputBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    isPassword: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    val borderColor = if (errorMessage != null) Color(0xFFF43F5E) else Color.White.copy(alpha = 0.12f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfacePillInactive)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondaryGray,
                        letterSpacing = 0.2.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryWhite
                        ),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        visualTransformation = if (isPassword && !isPasswordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                        cursorBrush = SolidColor(AccentSilver),
                        decorationBox = { innerTextField ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = TextSecondaryGray.copy(alpha = 0.45f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Show / Hide Password Action Text (Reference Image)
                if (isPassword && onTogglePasswordVisibility != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPasswordVisible) "Hide" else "Show",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryWhite,
                        modifier = Modifier
                            .clickable(onClick = onTogglePasswordVisibility)
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontSize = 12.sp,
                color = Color(0xFFF43F5E),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}
