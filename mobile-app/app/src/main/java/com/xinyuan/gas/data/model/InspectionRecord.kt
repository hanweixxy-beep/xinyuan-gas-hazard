package com.xinyuan.gas.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.xinyuan.gas.data.sync.SyncStatus
import java.util.UUID

@Entity(tableName = "inspection_records")
data class InspectionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 用户信息
    val customerName: String = "",
    val customerPhone: String = "",
    val customerType: String = "",
    val customerAddress: String = "",

    // 检查信息
    val photoPath: String = "",       // 逗号分隔多张照片路径
    val inspectorName: String = "",
    val inspectionDate: Long = System.currentTimeMillis(),

    // 隐患部位
    val hazardAlarm: String = "",     // 报警器: 无报警器/合格
    val hazardPipeline: String = "",  // 管道气
    val hazardStove: String = "",     // 灶具: 无熄保/合格
    val hazardHose: String = "",      // 软管: 过长/合格/非国标
    val hazardConnector: String = "", // 接头: 三通/合格
    val hazardValve: String = "",     // 阀门: 中压阀/正常使用

    // 用气情况
    val useOil: Boolean = false,
    val useElectric: Boolean = false,
    val useLPG: Boolean = false,
    val monthlyGasUsage: String = "",

    // 配送信息
    val deliveryPerson: String = "",

    // 整改信息
    val isRectified: String = "未整改",  // 未整改/已整改
    val rectificationDate: Long = 0L,

    // 其他
    val remark: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // 云端同步预留字段
    val uuid: String = UUID.randomUUID().toString(),
    val syncStatus: String = SyncStatus.PENDING,
    val lastSyncTime: Long = 0L,
    val remoteId: String = ""
)
