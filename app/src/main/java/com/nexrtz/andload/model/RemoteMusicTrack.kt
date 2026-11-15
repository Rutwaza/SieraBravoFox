package com.nexrtz.andload.model

data class RemoteMusicTrack(
    val identifier: String,
    val title: String,
    val artist: String,
    val year: String?,
    val licenseUrl: String?,
    val filename: String,
    val format: String,
    val downloadUrl: String
)
