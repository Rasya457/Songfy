package com.lagu.songfy.presentation.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lagu.songfy.data.local.datastore.UserSavedArtist
import com.lagu.songfy.presentation.theme.BackgroundNearBlack

data class ArtistPickerItem(
    val name: String,
    val imageUrl: String
)

@Composable
fun ArtistPickerScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onDone: (List<UserSavedArtist>) -> Unit = {}
) {
    BackHandler {
        onBackClick()
    }

    var searchQuery by remember { mutableStateOf("") }
    val selectedArtists = remember { mutableStateListOf<ArtistPickerItem>() }

    // Curated artist list matching user screenshot & popular Indonesian & international artists
    val defaultArtists = remember {
        listOf(
            ArtistPickerItem("Magnolia Celebration", "https://yt3.googleusercontent.com/czZkYw2CbA4bp9uZXpUEUJ54b7y_iwYjUrn5CNM50AtaZMjU2um9HqGeMCKPnbUUKAXoAE-vV5p0eMs=w500-h500-l90-rj"),
            ArtistPickerItem("Reality Club", "https://lh3.googleusercontent.com/YfD3p4VViGZEJw-d5IrbyTjj7YFfd1LnpLP6BU2hFm0WGbHM25ot2BonJZHNV6CeGXjJ_zI5OZVXXyW0=w500-h500-p-l90-rj"),
            ArtistPickerItem("The Weeknd", "https://lh3.googleusercontent.com/U-SAmNOu4TynE818gLCfKsuHZ0U5YNEtO9mrjSI9WCCKERs98LzrCal5kajBBTQNwdcisoB2Bn-pHp4=w500-h500-p-l90-rj"),
            ArtistPickerItem("Post Malone", "https://lh3.googleusercontent.com/48LfK4z6o-CCEWgHQnQfg0ltcT9tbZSN0qjSh0FSJsJI5GF48j2-pH219ciG1ML-PI80ZGD4Vz6sjg=w500-h500-p-l90-rj"),
            ArtistPickerItem("Charlie Puth", "https://lh3.googleusercontent.com/Wqr_bvGshelPD-n5OohFA1Au6r130npkqQX3BS0bEb4l373-FVX9ZhkhWykNKdq3CrNCF6ubJNTLVwI=w500-h500-p-l90-rj"),
            // Index 5 is the special recommendation blue circle
            ArtistPickerItem("Hindia", "https://yt3.googleusercontent.com/8ImMAMQSD4FA6-gdqCZWSFaB-drHvkdfiFcFAk7Mcyy56ctfWD-Xxno-CHfGC4L6Ql8aR61XT0vX0F4b=w500-h500-p-l90-rj"),
            ArtistPickerItem("Raim Laode", "https://lh3.googleusercontent.com/WP-k6kFk2XdH_l41tw8zWFNe9M-TVTnmfPCuqHJLj5apoMbsfUz6-UR9ICwspp2chE6_ra2ZB-nccA=w500-h500-p-l90-rj"),
            ArtistPickerItem("Taylor Swift", "https://yt3.googleusercontent.com/RCpTA6EXJQyjVFDosWOKa2SMmqkua_lA9mHPDWWciLwgqpZLz-k8rXWRF_367trrQ7up9BUwCbk6kRk=w500-h500-p-l90-rj"),
            ArtistPickerItem("Sal Priadi", "https://lh3.googleusercontent.com/wmItRT4hTCJrmnlsh_JBgOeBXww9mquXhrNR0oW3_hPW9LsZ5Z2grMij01EaENdt6ensOJfKm-OCBKqJ=w500-h500-p-l90-rj"),
            ArtistPickerItem("Tulus", "https://yt3.googleusercontent.com/h8P1jEIZLM8lkMxNA6Nbq98b43wcqllJSNmcZTCRPAB-F6rG_0Nqw5w7fwou0PN1QGwSW5viwWD5NV0=w500-h500-p-l90-rj"),
            ArtistPickerItem("Nadhif Basalamah", "https://yt3.googleusercontent.com/jjFbDHc_GFI6lVSSRPGWMrh71fJ16iZYMccLFbkN_Jq6uR-QYXzgRwFDSuZeDpOuAbIIzNnPAPDZgAHv=w500-h500-p-l90-rj"),
            ArtistPickerItem("Bernadya", "https://lh3.googleusercontent.com/hxROE1fvLWSxUYAFV3IgMp5vvjN91Jx6oS6uwSVyYN9_fb3I6PLaRa3Ufb4A0awxq4J5UoPFQAQM-Q=w500-h500-p-l90-rj"),
            ArtistPickerItem("Sheila On 7", "https://lh3.googleusercontent.com/ZBI5XEhdUvkE59Kr7HKZYIjoopuA_kC0uPAWNsEw-EmLfTtRaXuDEv-qYWTv_lahkpA8Yuhe7P-sO2Y=w500-h500-p-l90-rj"),
            ArtistPickerItem("Bruno Mars", "https://lh3.googleusercontent.com/hnefGBrazRhn4Z92bdSZBUENl40ONjRiVDsmZKZh-WZ2iCKE-2c7KKR7SNcZfzLHoRyB3E6as8L87YA=w500-h500-p-l90-rj"),
            ArtistPickerItem("Coldplay", "https://lh3.googleusercontent.com/IOKuXtp8PCQ_Fc-vaRKm3sKIXBxFV51gZheLTH5br-YGnWHFQf_Jywcuk7wbprYRoEbQyS_XZY6-nMJX=w500-h500-p-l90-rj"),
            ArtistPickerItem("Billie Eilish", "https://lh3.googleusercontent.com/tQC4rOL6xz6FhmFr0ggQExxyGbYSOsyveXVSnPBh2WjEyIzQ9pMHablLJ-0GlMBrLBlBrbWQGmzrV6KN=w500-h500-p-l90-rj"),
            ArtistPickerItem("NIKI", "https://yt3.googleusercontent.com/gKAVVgmPq8rRVjGXzkiaSxnHjfBdOBbl3kf1Nz2cel7vobVD5yzSVPE-Xr4c3brFq2nZ9xbbloRbPg=w500-h500-p-l90-rj"),
            ArtistPickerItem("Juicy Luicy", "https://yt3.googleusercontent.com/DDebW5VciXI_oMRQC1cRIWlpDIWVaS8c_CbcCkf89YeHVziP9lkgA0xUZmmVRxGKmC3qmppuMtvIsa5U=w500-h500-p-l90-rj"),
            ArtistPickerItem("Pamungkas", "https://yt3.googleusercontent.com/taxtgIghV9GbhxPOJ2_Utqh8T72EEBcGynoNX2dw9z-sSurU2GIW7vEM06ZYHJLOtcIOXvQZTpkfoJc=w500-h500-p-l90-rj"),
            ArtistPickerItem(".Feast", "https://lh3.googleusercontent.com/v78O_zDgEMvUp35uR0s0fU9pyvRJkELYJKKfYS33Kg1NpQrE4W-dzuS3CHuwWTdYsIs5RzwNpkiekcE=w500-h500-p-l90-rj")
        )
    }

    val filteredArtists = remember(searchQuery, defaultArtists) {
        if (searchQuery.isBlank()) {
            defaultArtists
        } else {
            defaultArtists.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundNearBlack)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Header: Back icon (optional) + Title
            item(span = { GridItemSpan(3) }) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.offset(x = (-8).dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Pilih artis lain yang kamu\nsuka.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Clean White Pill Search Bar matching screenshot
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = Color(0xFF1E1E1E),
                                modifier = Modifier.size(24.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cari",
                                        color = Color(0xFF757575),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color.Black,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(Color.Black),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus pencarian",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Custom search item if query has no exact match
            if (searchQuery.isNotBlank() && filteredArtists.none { it.name.equals(searchQuery.trim(), ignoreCase = true) }) {
                val customItem = ArtistPickerItem(
                    name = searchQuery.trim(),
                    imageUrl = "https://yt3.googleusercontent.com/POeZcjvT6yFIFhptgkVppyR_VHgUPgNGDOF0FaQZtpS_tkAV4JYFPGHAtr5lPogUT6kacE-h6w=w500-h500-l90-rj"
                )
                val isSelected = selectedArtists.any { it.name.equals(customItem.name, ignoreCase = true) }
                item {
                    ArtistCircleCard(
                        artist = customItem,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                selectedArtists.removeAll { it.name.equals(customItem.name, ignoreCase = true) }
                            } else {
                                selectedArtists.add(customItem)
                            }
                        }
                    )
                }
            }

            // Artist items with special blue tile at position 5 if not searching
            var specialTileRendered = false
            itemsIndexed(filteredArtists) { index, artist ->
                // In screenshot, row 2 column 3 (index 5) is the blue circle: "Artis lain yang mungkin kamu suka"
                if (searchQuery.isBlank() && index == 5 && !specialTileRendered) {
                    specialTileRendered = true
                    SpecialSuggestionTile()
                }

                val isSelected = selectedArtists.any { it.name.equals(artist.name, ignoreCase = true) }
                ArtistCircleCard(
                    artist = artist,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            selectedArtists.removeAll { it.name.equals(artist.name, ignoreCase = true) }
                        } else {
                            selectedArtists.add(artist)
                        }
                    }
                )
            }
        }

        // Floating Bottom "Selesai" Button matching screenshot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    val result = selectedArtists.map {
                        UserSavedArtist(
                            name = it.name,
                            imageUrl = it.imageUrl
                        )
                    }
                    onDone(result)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 13.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "Selesai",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun ArtistCircleCard(
    artist: ArtistPickerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, Color(0xFF1DB954), CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = artist.imageUrl,
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Spotify-style selection checkmark overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Dipilih",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun SpecialSuggestionTile() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .background(Color(0xFF283FBA))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Artis lain yang\nmungkin kamu\nsuka",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Empty spacer to align with other cards that have artist name
        Spacer(modifier = Modifier.height(16.dp))
    }
}
