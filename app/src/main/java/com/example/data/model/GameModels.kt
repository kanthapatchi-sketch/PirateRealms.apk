package com.example.data.model

import androidx.annotation.DrawableRes
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CaptainId(
    val id: String,
    val displayName: String,
    val thaiTitle: String,
    val skillName: String,
    val skillDescription: String,
    val accentHex: Long,
    @DrawableRes val avatarRes: Int
) {
    IGNIS(
        id = "ignis",
        displayName = "Captain Ignis",
        thaiTitle = "จอมโจรเพลิง",
        skillName = "Corsair Takeover -20%",
        skillDescription = "ลดค่าซื้อต่อเกาะศัตรู (Takeover) ลง 20%",
        accentHex = 0xFFD64045,
        avatarRes = R.drawable.img_captain_ignis
    ),
    MARINA(
        id = "marina",
        displayName = "Marina Siren",
        thaiTitle = "เงือกมนตรา",
        skillName = "Siren Tide +25%",
        skillDescription = "รับโบนัสเงินเดือนเพิ่ม +25% ทุกครั้งที่ผ่านท่าเรือหลวง",
        accentHex = 0xFF2B8CBE,
        avatarRes = R.drawable.img_captain_marina
    ),
    BARNABY(
        id = "barnaby",
        displayName = "Barnaby",
        thaiTitle = "ช่างปืนใหญ่คนแคระ",
        skillName = "Master Forge -20%",
        skillDescription = "ลดค่าก่อสร้างและอัปเกรดเกาะทุกระดับลง 20%",
        accentHex = 0xFFE08E26,
        avatarRes = R.drawable.img_captain_barnaby
    ),
    VANE(
        id = "vane",
        displayName = "Shadow Vane",
        thaiTitle = "ต้นหนวิญญาณ",
        skillName = "Phantom Dice 100%",
        skillDescription = "ควบคุมเต๋าคี่/คู่แม่นยำ 100% และมีชาร์จใช้งานมากกว่าคนอื่น",
        accentHex = 0xFF8C52FF,
        avatarRes = R.drawable.img_captain_vane
    );

    companion object {
        fun fromId(id: String?): CaptainId = entries.firstOrNull { it.id == id } ?: IGNIS
    }
}

enum class BotDifficulty(
    val id: String,
    val thaiLabel: String,
    val weight: Double,
    val normalizedLabel: String
) {
    EASY("easy", "ง่าย (Easy)", 0.25, "25% (20.8%)"),
    MEDIUM("medium", "ปานกลาง (Medium)", 0.40, "40% (33.3%)"),
    HARD("hard", "ยาก (Hard)", 0.55, "55% (45.8%)");

    companion object {
        fun fromId(id: String?): BotDifficulty = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class BotSlotSelection(val label: String) {
    AUTO_RANDOM("สุ่มออโต้ (Auto)"),
    EASY("ง่าย (25%)"),
    MEDIUM("ปานกลาง (40%)"),
    HARD("ยาก (55%)"),
    OFF("ปิดช่อง")
}

enum class TileType {
    START,
    ISLAND,
    FORTUNE,
    JAIL,
    TAVERN,
    FESTIVAL,
    WHIRLPOOL,
    KRAKEN_TAX
}

enum class ColorGroup(val thaiName: String, val colorHex: Long) {
    CORAL("หมู่เกาะปะการัง", 0xFFE76F51),
    AMBER("อ่าวอำพัน", 0xFFF4A261),
    EMERALD("ลากูนมรกต", 0xFF2A9D8F),
    SAPPHIRE("น่านน้ำไพลิน", 0xFF3A86FF),
    AMETHYST("นครลอยฟ้า", 0xFF9B5DE5),
    GOLD("วิหารทองคำ", 0xFFE9C46A)
}

enum class BuildingTier(
    val level: Int,
    val thaiName: String,
    val buildCostRatio: Double,
    val tollMultiplier: Double
) {
    NONE(0, "เกาะเปล่า", 0.0, 0.5),
    CAMP(1, "แคมป์โจรสลัด", 0.5, 1.0),
    OUTPOST(2, "ป้อมปราการ", 0.8, 2.2),
    CASTLE(3, "ปราสาทโจรสลัด", 1.2, 4.0),
    LANDMARK(4, "แลนด์มาร์ก", 1.8, 6.5);

    companion object {
        fun fromLevel(level: Int): BuildingTier = entries.firstOrNull { it.level == level } ?: NONE
    }
}

enum class DiceMode(val thaiLabel: String) {
    NORMAL("ปกติ"),
    ODD("เต๋าคี่"),
    EVEN("เต๋าคู่")
}

enum class StickerEmotion(val id: String, val emoji: String, val label: String) {
    AHOY("ahoy", "🏴‍☠️", "Ahoy!"),
    LAUGH("laugh", "😂", "ฮ่าๆ เสร็จข้า!"),
    GOLD("treasure", "💰", "รวยแล้ว!"),
    SHOCK("shock", "😱", "ค่าผ่านทางโหด!"),
    SALUTE("salute", "🫡", "เดินเรือเยี่ยม!"),
    KRAKEN("kraken", "🐙", "ระวังคราเคน!")
}

data class BoardTile(
    val index: Int,
    val row: Int,
    val col: Int,
    val name: String,
    val subtitle: String,
    val type: TileType,
    val colorGroup: ColorGroup? = null,
    val basePrice: Int = 0,
    val baseToll: Int = 0
)

data class PlayerState(
    val slotIndex: Int,
    val uid: String,
    val name: String,
    val captain: CaptainId,
    val isBot: Boolean,
    val botDifficulty: BotDifficulty = BotDifficulty.MEDIUM,
    val gold: Int = 2000,
    val position: Int = 0,
    val inJail: Boolean = false,
    val jailTurns: Int = 0,
    val angelCards: Int = 1,
    val specialDiceCharges: Int = 3,
    val doublesStreak: Int = 0,
    val bankrupt: Boolean = false
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "slotIndex" to slotIndex.toLong(),
        "uid" to uid,
        "name" to name,
        "captainId" to captain.id,
        "isBot" to isBot,
        "botDifficulty" to botDifficulty.id,
        "gold" to gold.toLong(),
        "position" to position.toLong(),
        "inJail" to inJail,
        "jailTurns" to jailTurns.toLong(),
        "angelCards" to angelCards.toLong(),
        "specialDiceCharges" to specialDiceCharges.toLong(),
        "doublesStreak" to doublesStreak.toLong(),
        "bankrupt" to bankrupt
    )

    companion object {
        fun fromFirestoreMap(map: Map<String, Any?>, fallbackIndex: Int): PlayerState {
            return PlayerState(
                slotIndex = (map["slotIndex"] as? Number)?.toInt() ?: fallbackIndex,
                uid = (map["uid"] as? String).orEmpty(),
                name = (map["name"] as? String) ?: "Pirate ${fallbackIndex + 1}",
                captain = CaptainId.fromId(map["captainId"] as? String),
                isBot = (map["isBot"] as? Boolean) ?: false,
                botDifficulty = BotDifficulty.fromId(map["botDifficulty"] as? String),
                gold = (map["gold"] as? Number)?.toInt() ?: 2000,
                position = (map["position"] as? Number)?.toInt() ?: 0,
                inJail = (map["inJail"] as? Boolean) ?: false,
                jailTurns = (map["jailTurns"] as? Number)?.toInt() ?: 0,
                angelCards = (map["angelCards"] as? Number)?.toInt() ?: 1,
                specialDiceCharges = (map["specialDiceCharges"] as? Number)?.toInt() ?: 3,
                doublesStreak = (map["doublesStreak"] as? Number)?.toInt() ?: 0,
                bankrupt = (map["bankrupt"] as? Boolean) ?: false
            )
        }
    }
}

@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredCaptain: String = "ignis",
    val wins: Long = 0L,
    val gamesPlayed: Long = 0L,
    val highestGold: Long = 2000L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoomDoc(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val hostId: String = "",
    val status: String = "waiting",
    val isPublic: Boolean = true,
    val maxPlayers: Long = 4L,
    val memberUids: List<String> = emptyList(),
    val players: List<Map<String, Any?>> = emptyList(),
    val tileOwners: List<String> = List(28) { "" },
    val tileLevels: List<Long> = List(28) { 0L },
    val tileFestivals: List<Long> = List(28) { 1L },
    val currentTurnIndex: Long = 0L,
    val turnNumber: Long = 0L,
    val lastDice1: Long = 0L,
    val lastDice2: Long = 0L,
    val lastLog: String = "",
    val winnerUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoomMessageDoc(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val captainId: String = "ignis",
    val text: String = "",
    val stickerId: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomId" to roomId,
        "senderId" to senderId,
        "senderName" to senderName,
        "captainId" to captainId,
        "text" to text,
        "stickerId" to stickerId,
        "memberUids" to memberUids,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
