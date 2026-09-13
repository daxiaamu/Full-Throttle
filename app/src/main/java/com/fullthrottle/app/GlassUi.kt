package com.fullthrottle.app

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.view.View
import android.widget.SeekBar
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compose theme backed by Kyant's Backdrop, independent of the classic View theme. */
class GlassUi(private val activity: MainActivity) {
    private val preferences get() = activity.getSharedPreferences("settings", 0)
    private val dark = activity.resources.configuration.uiMode and 48 == 32
    private val ink = Color(activity.getColor(R.color.app_text))
    private val muted = Color(activity.getColor(R.color.app_text_secondary))
    private val accent = Color(activity.getColor(R.color.app_accent))
    private val warning = Color(activity.getColor(R.color.app_warning))
    private var revision by mutableIntStateOf(0)
    private var pending by mutableStateOf<String?>(null)
    val view: View = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { Screen() }
    }
    fun refresh() { pending = null; revision++ }
    private data class Reading(
        val battery: String, val system: String, val watts: String, val powerLabel: String,
        val equation: String, val unit: String, val active: Boolean, val state: String,
        val duration: String, val finish: String, val hardware: String, val heat: String,
        val level: Float, val target: Int)
    private fun read(): Reading {
        val battery = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        DrainService.sampleBattery(activity, battery)
        val raw = activity.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val mode = preferences.getInt("currentUnit", BatteryPower.AUTO)
        val ma = BatteryPower.usesMilliamps(mode, Build.MANUFACTURER, Build.MODEL)
        val valid = raw != Int.MIN_VALUE && voltage > 0
        val running = DrainService.active
        val plugged = DrainService.plugged
        val remaining = DrainService.remaining
        fun format(pattern: String, vararg values: Any) = String.format(Locale.CHINA, pattern, *values)
        return Reading(
            if(DrainService.estimatedLevel.isFinite()) format("%.2f%%", DrainService.estimatedLevel) else "--.--%",
            if(DrainService.level >= 0) "系统电量：" + DrainService.level + "%" else "系统电量不可用",
            if(valid) format("%.2f W", BatteryPower.watts(raw, voltage, ma)) else "-- W",
            if(!valid) "电流数据暂不可用" else if(plugged) "电池净功率 · 已接电" else "当前功耗 · 放电",
            if(valid) format("%.3f V × %.3f A", voltage / 1000.0, BatteryPower.amps(raw, ma)) else "-- V × -- A",
            "电流单位：" + (if(ma) "mA" else "µA") + (if(mode == BatteryPower.AUTO) "（自动）" else "（手动）"),
            running, DrainService.message,
            if(!running) "开启后测算" else if(plugged) "接通电源，暂停预测" else if(remaining < 0) "正在采样…" else DrainService.duration(remaining),
            if(running && !plugged && remaining >= 0) "预计停止于 " + SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(System.currentTimeMillis() + remaining))
            else if(running && !plugged) "根据实际掉电速度计算，通常需 1–3 分钟" else "预计停止时间  --:--",
            if(running) "CPU " + Runtime.getRuntime().availableProcessors() + " 线程  /  " + DrainService.gpu else "CPU / GPU 待机",
            format("电池温度 %.1f°C  ·  %s", DrainService.temperature, if(plugged) "已连接电源" else "使用电池"),
            (if(DrainService.estimatedLevel.isFinite()) DrainService.estimatedLevel.toFloat() else DrainService.level.toFloat()).coerceIn(0f,100f),
            DrainService.target(activity))
    }
    @Composable private fun Text(value: String, size: Int = 14, color: Color = ink,
        modifier: Modifier = Modifier, bold: Boolean = false, align: TextAlign = TextAlign.Start) {
        BasicText(value, modifier, style = TextStyle(color=color,fontSize=size.sp,
            fontWeight=if(bold) FontWeight.SemiBold else FontWeight.Normal,textAlign=align,
            fontFeatureSettings="tnum",lineHeight=(size*1.38f).sp))
    }
    @Composable private fun GlassControl(backdrop: Backdrop, description: String, modifier: Modifier,
        shape: Shape = CircleShape, tint: Color = Color.Transparent,
        onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val progress by animateFloatAsState(if(pressed) 1f else 0f,spring(stiffness=600f),label="glass press")
        Box(modifier.drawBackdrop(backdrop=backdrop,shape={shape},
            effects={vibrancy();blur(2.dp.toPx());lens(10.dp.toPx(),18.dp.toPx())},
            layerBlock={scaleX=1f+progress*.025f;scaleY=1f-progress*.015f},
            onDrawSurface={
                drawRect(if(dark) Color.White.copy(alpha=.045f) else Color.White.copy(alpha=.16f))
                drawRect(tint)
                if(progress>0f) drawRect(Color.White.copy(alpha=progress*.16f))
            }).clip(shape).semantics{contentDescription=description}
            .clickable(source,indication=null,role=Role.Button,onClick=onClick),
            contentAlignment=Alignment.Center,content=content)
    }
    @Composable private fun Background(modifier: Modifier) {
        Canvas(modifier) {
            drawRect(if(dark) Color(0xFF101C2E) else Color(0xFFE8EEF6))
            val ribbon=Path().apply {
                moveTo(size.width*.9f,-size.height*.15f)
                cubicTo(size.width*.10f,size.height*.10f,size.width*1.15f,size.height*.4f,size.width*.25f,size.height*.68f)
                cubicTo(-size.width*.2f,size.height*.87f,size.width*.6f,size.height*1.1f,size.width*1.1f,size.height*1.2f)
                lineTo(size.width*1.4f,size.height*1.2f);lineTo(size.width*1.4f,-size.height*.15f);close()
            }
            drawPath(ribbon,Brush.verticalGradient(if(dark)
                listOf(Color(0xFF274D78),Color(0xFF183F49),Color(0xFF243457))
                else listOf(Color(0xFFBDD4EC),Color(0xFFC5DED9),Color(0xFFD5DAF0))))
            drawCircle(Brush.radialGradient(
                listOf(if(dark) Color(0x333B7CAA) else Color(0x88FFFFFF),Color.Transparent),
                center=Offset(size.width*.1f,size.height*.38f),radius=size.width*.8f),
                radius=size.width*.8f,center=Offset(size.width*.1f,size.height*.38f))
        }
    }
    @Composable private fun Screen() {
        val data=remember(revision){read()}
        var menu by remember{mutableStateOf(false)}
        var settings by remember{mutableStateOf(false)}
        val background=rememberLayerBackdrop()
        val content=rememberLayerBackdrop()
        BackHandler(menu || settings){menu=false;settings=false}
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide=maxWidth>=600.dp
            val compact=wide && maxHeight<520.dp
            Box(Modifier.fillMaxSize().layerBackdrop(content).then(if(menu || settings) Modifier.clearAndSetSemantics {} else Modifier)) {
                Background(Modifier.fillMaxSize().layerBackdrop(background))
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal=24.dp,vertical=if(compact) 8.dp else 18.dp),
                    horizontalAlignment=Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max=1072.dp).fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("油门拉满",if(compact) 23 else 29,bold=true)
                                if(!compact) Text("FULL THROTTLE  /  电池放电工具",12,muted)
                            }
                            GlassControl(background,"切换主题",Modifier.size(48.dp),onClick={menu=!menu}) {
                                Canvas(Modifier.size(24.dp)){repeat(3){drawCircle(ink,1.7.dp.toPx(),Offset(size.width*(.2f+it*.3f),size.height/2))}}
                            }
                        }
                        Spacer(Modifier.height(if(compact) 14.dp else 26.dp))
                        if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                            Column(Modifier.weight(1.1f)){Controls(data,background,compact)}
                            Column(Modifier.weight(1f)){Information(data,background){settings=true}}
                        } else {
                            Controls(data,background,false)
                            Spacer(Modifier.height(20.dp))
                            Information(data,background){settings=true}
                        }
                    }
                }
            }
            AnimatedVisibility(menu,enter=fadeIn(),exit=fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().clickable(remember{MutableInteractionSource()},null){menu=false})
                    Column(Modifier.safeDrawingPadding().padding(top=78.dp,end=24.dp).align(Alignment.TopEnd)
                        .width(208.dp).drawBackdrop(content,{RoundedCornerShape(28.dp)},
                            effects={vibrancy();blur(12.dp.toPx());lens(12.dp.toPx(),20.dp.toPx())},
                            onDrawSurface={drawRect(if(dark) Color(0x66111F31) else Color(0x80FFFFFF))})
                        .semantics{paneTitle="主题"}.padding(12.dp)) {
                        Text("主题",12,muted,Modifier.padding(start=12.dp,bottom=8.dp))
                        ThemeOption("经典",false){menu=false;preferences.edit().putBoolean("glassTheme",false).apply();activity.recreate()}
                        ThemeOption("液态玻璃",true){menu=false}
                    }
                }
            }
            if(settings) Settings(content){settings=false}
        }
    }
    @Composable private fun ThemeOption(name: String,selected: Boolean,onClick: () -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(16.dp))
            .selectable(selected=selected,role=Role.RadioButton,onClick=onClick).padding(horizontal=12.dp),
            verticalAlignment=Alignment.CenterVertically) {
            Text(if(selected) "✓" else "",18,accent,Modifier.width(28.dp));Text(name,16)
        }
    }
    @Composable private fun Controls(data: Reading,backdrop: Backdrop,compact: Boolean) {
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val numberSize=minOf(36,((maxWidth.value/2-10)/5.2f).toInt())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("剩余电量（估算）",12,muted)
                        Text(data.battery,numberSize,modifier=Modifier.heightIn(min=52.dp),bold=true)
                        Text(data.system,11,muted)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(data.powerLabel,12,muted)
                        Text(data.watts,numberSize,accent,Modifier.heightIn(min=52.dp),bold=true)
                        Text(data.equation,11,muted);Text(data.unit,11,muted)
                    }
                }
            }
            Spacer(Modifier.height(if(compact) 8.dp else 22.dp))
            val diameter=if(compact) 132.dp else 216.dp
            Box(Modifier.size(diameter),contentAlignment=Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke=2.dp.toPx()
                    drawArc(ink.copy(alpha=.1f),-90f,360f,false,Offset(stroke,stroke),Size(size.width-stroke*2,size.height-stroke*2),style=Stroke(stroke))
                    drawArc(accent.copy(alpha=if(data.active) 1f else .55f),-90f,data.level*3.6f,false,
                        Offset(stroke,stroke),Size(size.width-stroke*2,size.height-stroke*2),style=Stroke(stroke,cap=StrokeCap.Round))
                }
                GlassControl(backdrop,if(data.active) "停止耗电" else "开启耗电",Modifier.size(diameter-24.dp),
                    tint=if(data.active) accent.copy(alpha=.14f) else Color.Transparent,onClick={
                        pending=if(DrainService.active) "正在停止…" else "正在启动…"
                        if(DrainService.active) activity.stopService(Intent(activity,DrainService::class.java))
                        else activity.requestStart()
                    }) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        Canvas(Modifier.size(if(compact) 34.dp else 54.dp)) {
                            val line=3.5.dp.toPx()
                            drawArc(ink,-45f,270f,false,Offset(line,size.height*.2f),Size(size.width-line*2,size.height*.75f-line),style=Stroke(line,cap=StrokeCap.Round))
                            drawLine(ink,Offset(size.width/2,0f),Offset(size.width/2,size.height*.48f),line,StrokeCap.Round)
                        }
                        Spacer(Modifier.height(if(compact) 6.dp else 18.dp))
                        Text(if(data.active) "停止耗电" else "开启耗电",if(compact) 14 else 18,bold=true)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(pending ?: data.state,15,if(data.active) accent else ink,align=TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            if(data.active) Text("手机会发热发烫，请注意通风散热",13,warning,align=TextAlign.Center)
            Spacer(Modifier.height(8.dp));Text(data.hardware,11,muted,align=TextAlign.Center)
        }
    }
    @Composable private fun Information(data: Reading,backdrop: Backdrop,openSettings: () -> Unit) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth().background(if(dark) Color(0x4028384D) else Color(0x88FFFFFF),RoundedCornerShape(26.dp)).padding(20.dp)) {
                Text("距停止电量预计还需",13,muted);Text(data.duration,24,bold=true)
                Spacer(Modifier.height(6.dp));Text(data.finish,13,muted)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text("自动停止电量",13,muted);Text(data.target.toString()+"%",24,bold=true)}
                GlassControl(backdrop,"耗电设置",Modifier.width(80.dp).height(48.dp),RoundedCornerShape(24.dp),onClick=openSettings){Text("设置",15,accent,bold=true)}
            }
            Text(data.heat,12,muted)
            Text("运行时前后台均保持屏幕常亮，本页使用最高亮度。后台持续运行并显示常驻通知，可从通知停止。手动锁屏及系统管控仍由手机决定。",12,muted)
        }
    }
    @Composable private fun Settings(backdrop: Backdrop,dismiss: () -> Unit) {
        var target by remember{mutableIntStateOf(DrainService.target(activity))}
        var unit by remember{mutableIntStateOf(preferences.getInt("currentUnit",BatteryPower.AUTO))}
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.12f)).clickable(remember{MutableInteractionSource()},null,onClick=dismiss))
            Column(Modifier.safeDrawingPadding().padding(24.dp).widthIn(max=420.dp).fillMaxWidth()
                .drawBackdrop(backdrop,{RoundedCornerShape(30.dp)},effects={blur(16.dp.toPx());lens(10.dp.toPx(),16.dp.toPx())},
                    onDrawSurface={drawRect(if(dark) Color(0xCC142235) else Color(0xDCFFFFFF))})
                .semantics{paneTitle="耗电设置"}.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Text("耗电设置",22,bold=true);Spacer(Modifier.height(16.dp))
                Text("自动停止电量",13,muted);Text(target.toString()+"%",30,bold=true)
                AndroidView(factory={context->SeekBar(context).apply {
                    max=99;progress=target-1;contentDescription="停止电量，1% 至 100%"
                    setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: SeekBar?,value: Int,fromUser: Boolean){if(fromUser) target=value+1}
                        override fun onStartTrackingTouch(bar: SeekBar?) {}
                        override fun onStopTrackingTouch(bar: SeekBar?) {}
                    })
                }},update={it.progress=target-1},modifier=Modifier.fillMaxWidth().height(48.dp))
                Text("剩余电量低于或等于此值时停止。",12,muted);Spacer(Modifier.height(16.dp))
                Text("电流单位（影响功耗显示）",13,muted)
                val autoMa=BatteryPower.usesMilliamps(BatteryPower.AUTO,Build.MANUFACTURER,Build.MODEL)
                listOf("自动（本机使用 "+(if(autoMa) "mA" else "µA")+"）","µA · Android 标准","mA · 部分厂商系统").forEachIndexed{index,label->
                    ThemeOption(label,index==unit){unit=index}
                }
                Text("一加 8T 默认按 mA 换算；双电芯不自动乘 2。",12,muted);Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    Text("取消",16,muted,Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick=dismiss).padding(14.dp))
                    Text("保存",16,accent,Modifier.clip(RoundedCornerShape(20.dp)).clickable{
                        preferences.edit().putInt("target",target).putInt("currentUnit",unit).apply()
                        if(DrainService.active) activity.startService(Intent(activity,DrainService::class.java))
                        refresh();dismiss()
                    }.padding(14.dp),bold=true)
                }
            }
        }
    }
}