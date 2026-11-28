package com.lucida.camera;

import java.util.ArrayList;
import java.util.List;

/** 自动生成，请勿手改 —— 改 tools/gen_settings.py 后重新生成。每个集合一个方法，避免超出 Java 单方法 64KB 限制。 */
final class SettingsData {
    private SettingsData() {}

    static SettingsRegistry.Collection build_account() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("acct", "登录账号", "等登录与会员功能上线后开放", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "", opts, notes, "", false, false, "person.crop.circle", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        return new SettingsRegistry.Collection("account", "账号", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static SettingsRegistry.Collection build_appearance() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("frosted", "毛玻璃", "半透明深色 + 高光描边", "毛玻璃", false));
                opts.add(new SettingsRegistry.Option("solid", "固态玻璃", "上一版的液态玻璃：取景画面按透镜折弯（原样保留）", "固态玻璃", false));
                opts.add(new SettingsRegistry.Option("liquid2", "液态玻璃", "新：SDF 圆角折射 + 边缘高光（安卓 13 及以上；低版本自动用固态玻璃）", "液态玻璃", false));
                opts.add(new SettingsRegistry.Option("plain", "默认面板", "关掉玻璃和模糊，取景和设置都用普通的深色样式（默认）", "默认面板", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("glassStyle", "界面风格", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "plain", opts, notes, "", true, false, "drop", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("界面风格", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("mid", "原中版", "默认皮肤", "原中版", false));
                opts.add(new SettingsRegistry.Option("fish", "大肥鱼", "带角色的皮肤", "大肥鱼", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("skin", "外观 / 皮肤", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "mid", opts, notes, "", false, false, "paintbrush", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("皮肤", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("glass", "默认（毛玻璃）", "面板使用半透明玻璃效果", "默认（毛玻璃）", false));
                opts.add(new SettingsRegistry.Option("dark", "黑色面板 UI", "纯黑色面板，去掉玻璃和模糊，更省性能", "黑色面板 UI", false));
                opts.add(new SettingsRegistry.Option("light", "白色面板 UI", "纯白色面板，去掉玻璃和模糊，更省性能", "白色面板 UI", false));
                List<String> notes = new ArrayList<String>();
                notes.add("选中纯色面板后所有界面改成纯色，去掉玻璃和模糊，更省性能，适合老设备。选默认就是玻璃效果。");
                items.add(new SettingsRegistry.Item("panelTheme", "面板样式", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "glass", opts, notes, "", true, false, "rectangle.fill", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("面板", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("uiScale", "界面大小", "等界面大小功能完成后开放。开放后会缩放全部界面（栏、面板、设置页、相册等；取景画面本身不变），设置页自己最小只缩到 30%。", SettingsRegistry.T_SLIDER, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "100", opts, notes, "", false, false, "arrow.up.left.and.arrow.down.right", 1, 200));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("fontScale", "字体大小", "等字体大小功能完成后开放。开放后会缩放全部界面文字，设置页里最小只缩到 30%。", SettingsRegistry.T_SLIDER, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "100", opts, notes, "", false, false, "textformat.size", 1, 200));
            }
            groups.add(new SettingsRegistry.Group("大小与字体", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("uiReset", "每一次打开都重置界面大小和字体大小", "等界面大小和字体大小功能完成后开放。调得太大点不到设置时，重启 App 就恢复 100%", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "1", opts, notes, "", false, false, "arrow.counterclockwise", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        return new SettingsRegistry.Collection("appearance", "界面与外观", SettingsRegistry.K_PAGE, "皮肤 · 面板 · 大小 · 字体", "界面风格和面板样式现在可以用；皮肤、大小和字体的设置还在适配中。", "paintbrush", groups);
    }

    static SettingsRegistry.Collection build_shoot() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("16:9", "16:9", "", "16:9", false));
                opts.add(new SettingsRegistry.Option("4:3", "4:3", "", "4:3", false));
                opts.add(new SettingsRegistry.Option("1:1", "1:1", "", "1:1", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("ratio", "画幅比例", "取景、拍照、录像、实况的成品都按这个比例。片门全开时固定为 4:3", SettingsRegistry.T_SEG, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "16:9", opts, notes, "", false, true, "aspectratio", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("sFull", "片门全开（单摄）", "单摄时用 4:3 整块传感器规格，成品 3:4。双摄的片门全开在「双摄设置」里，两者互不影响", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_SINGLE, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, true, "rectangle.expand.vertical", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("0", "关", "", "关", false));
                opts.add(new SettingsRegistry.Option("1", "自动", "", "自动", false));
                opts.add(new SettingsRegistry.Option("2", "开", "", "开", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("flash", "闪光灯", "拍照补光；自动 = 暗处才闪；拍实况时不起作用", SettingsRegistry.T_SEG, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "bolt", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("0", "关", "", "关", false));
                opts.add(new SettingsRegistry.Option("3", "3 秒", "", "3 秒", false));
                opts.add(new SettingsRegistry.Option("5", "5 秒", "", "5 秒", false));
                opts.add(new SettingsRegistry.Option("10", "10 秒", "", "10 秒", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("timerSec", "倒计时拍摄", "点快门后倒数，数到 0 自动拍；倒数中再点一下快门取消", SettingsRegistry.T_SEG, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "timer", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("countVibrate", "倒计时震动", "倒计时每数一秒轻震一下；关掉就只有数字，没有震动", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "1", opts, notes, "", false, false, "iphone.radiowaves.left.and.right", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("倒计时", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("grid", "取景网格线", "在取景框里画三分线，方便构图", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "grid", 0, 0));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("level", "水平仪", "取景框中间显示水平线：竖拿手机时线跟地平线平行，歪在 0.8° 内变黄；手机平放俯拍时变成圆圈 + 小圆点", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "level", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        return new SettingsRegistry.Collection("shoot", "取景与拍摄", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static SettingsRegistry.Collection build_photo() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("15", "15 帧", "最省内存和电量", "15 帧", false));
                opts.add(new SettingsRegistry.Option("30", "30 帧（推荐）", "流畅，内存适中", "30 帧", false));
                opts.add(new SettingsRegistry.Option("60", "60 帧", "最顺滑；每帧像素会比 30 帧略小", "60 帧", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("sLiveFps", "实况帧率", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_SINGLE, SettingsRegistry.S_LIVE, "30", opts, notes, "", true, false, "speedometer", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("实况帧率", "只影响之后拍的实况，也决定编辑后实况视频重新生成时的帧率。已拍好的 15 帧实况无法凭空补帧。", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("3", "3 秒（默认）", "从按下快门开始录 3 秒", "3 秒", false));
                opts.add(new SettingsRegistry.Option("5", "5 秒", "从按下快门开始录 5 秒", "5 秒", false));
                opts.add(new SettingsRegistry.Option("6", "6 秒", "从按下快门开始录 6 秒", "6 秒", false));
                opts.add(new SettingsRegistry.Option("8", "8 秒", "从按下快门开始录 8 秒", "8 秒", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("sLiveDur", "实况时长", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_SINGLE, SettingsRegistry.S_LIVE, "3", opts, notes, "", true, false, "timer", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("实况时长", "只影响之后拍的实况。安卓的动态照片从按下快门开始录，没有快门前 1.5 秒；时间越长，合成越久。", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("pair", "照片 + 视频", "实况存成一张照片和一段 3 秒视频两个文件，苹果 / 安卓都一样，都能保存", "照片 + 视频", false));
                List<String> notes = new ArrayList<String>();
                notes.add("原生 App：保存时会把照片 + 视频交给系统，写成系统「实况照片」。");
                items.add(new SettingsRegistry.Item("sLiveStill", "实况保存方式", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_SINGLE, SettingsRegistry.S_LIVE, "pair", opts, notes, "", false, false, "livephoto", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("实况保存", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("sLiveTimer", "实况计时显示", "拍实况时在取景框左上角显示已经拍到第几秒、有没有拍完", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_SINGLE, SettingsRegistry.S_LIVE, "1", opts, notes, "", false, false, "clock", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        return new SettingsRegistry.Collection("photo", "拍照与实况", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static SettingsRegistry.Collection build_video() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("audio", "录像时录制声音", "关闭后录下来的视频没有声音", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "1", opts, notes, "", false, false, "mic", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("录制", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("direct", "直录模式", "等直录模式完成后开放", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "1", opts, notes, "", false, false, "video", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "录像直接使用摄像头原始画面、由硬件编码，4K 与 60 帧最稳定。直录时录制中不能变焦；已放大、或所选比例与镜头原生画面不同时，会自动改用画布录制（4K 下帧率可能偏低）。", items));
        }
        return new SettingsRegistry.Collection("video", "录像", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static SettingsRegistry.Collection build_palette() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("order", "调色盘预设顺序", "拖动调整 13 个预设的顺序，调色盘里左右滑动就按这个顺序切换；「标准」固定在第一位", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "order", false, false, "circle.hexagongrid", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        return new SettingsRegistry.Collection("palette", "调色盘", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static SettingsRegistry.Collection build_zoom() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("0", "1× → 10×　快速变焦", "手指横向滑动约 1/3 屏宽，变焦从 1× → 10×", "1× → 10×", false));
                opts.add(new SettingsRegistry.Option("1", "1× → 15×　快速变焦", "手指横向滑动约 1/3 屏宽，变焦从 1× → 15×", "1× → 15×", false));
                opts.add(new SettingsRegistry.Option("2", "1× → 20×　快速变焦", "手指横向滑动约 1/3 屏宽，变焦从 1× → 20×", "1× → 20×", false));
                opts.add(new SettingsRegistry.Option("3", "1× → 25×　快速变焦", "手指横向滑动约 1/3 屏宽，变焦从 1× → 25×", "1× → 25×", false));
                opts.add(new SettingsRegistry.Option("4", "1× → 30×　极速变焦", "手指横向滑动约 1/3 屏宽，变焦从 1× → 30×", "1× → 30×", false));
                List<String> notes = new ArrayList<String>();
                notes.add("用于调节变焦转盘的跟手程度：档位越高，手指滑动同样距离，变焦变化越大。");
                items.add(new SettingsRegistry.Item("zoomSpeed", "变焦速度", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "4", opts, notes, "", false, false, "slider.horizontal.3", 0, 0));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("off", "关闭 0.5×", "设备没有超广角镜头时选这个，最小变焦为 1×", "关闭", false));
                opts.add(new SettingsRegistry.Option("0.6", "开启 0.6×", "适用于超广角为 0.6× 的安卓设备", "0.6×", false));
                opts.add(new SettingsRegistry.Option("0.5", "开启 0.5×", "标准超广角（iPhone 等，默认）", "0.5×", false));
                List<String> notes = new ArrayList<String>();
                notes.add("不同手机的超广角倍数不同。选对后，变焦条、转盘的最小倍数和画面视野会与镜头一致；若设备自己的最小变焦更高，会自动取更高的值。");
                items.add(new SettingsRegistry.Item("uwScale", "超广角倍数", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "0.5", opts, notes, "", false, false, "eye", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("变焦", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("auto", "自动", "先按机型推测，第一次切到长焦时再用画面比对校准", "自动", false));
                opts.add(new SettingsRegistry.Option("2", "2×", "iPhone 11 / 12 / 13 / 14 / 15 / 16 双摄", "2×", false));
                opts.add(new SettingsRegistry.Option("2.5", "2.5×", "iPhone 12 Pro Max", "2.5×", false));
                opts.add(new SettingsRegistry.Option("3", "3×", "iPhone 13 Pro、14 Pro、15 Pro", "3×", false));
                opts.add(new SettingsRegistry.Option("4", "4×", "iPhone 17 Pro 系列", "4×", false));
                opts.add(new SettingsRegistry.Option("5", "5×", "iPhone 15 Pro Max、16 Pro 系列", "5×", false));
                List<String> notes = new ArrayList<String>();
                notes.add("手机有长焦镜头时，变焦到这个倍数，就会换成长焦镜头；再往上放大（例如 30×）是在长焦画面上继续数码放大，比主摄直接放大更清晰。自动识别不准时请在这里手动选。");
                items.add(new SettingsRegistry.Item("teleScale", "长焦镜头倍数", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "auto", opts, notes, "", false, false, "scope", 0, 0));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("10", "大于 10× 变焦显示取景框", "默认", ">10×", false));
                opts.add(new SettingsRegistry.Option("20", "大于 20× 变焦显示取景框", "较晚出现", ">20×", false));
                opts.add(new SettingsRegistry.Option("25", "大于 25× 变焦显示取景框", "", ">25×", false));
                opts.add(new SettingsRegistry.Option("30", "大于 30× 变焦显示取景框", "", ">30×", false));
                opts.add(new SettingsRegistry.Option("50", "大于 50× 变焦显示取景框", "", ">50×", false));
                opts.add(new SettingsRegistry.Option("80", "大于 80× 变焦显示取景框", "", ">80×", false));
                opts.add(new SettingsRegistry.Option("100", "大于 100× 变焦显示取景框", "只在最大倍数附近出现", ">100×", false));
                List<String> notes = new ArrayList<String>();
                notes.add("放大到设定倍数后，取景画面左上角出现一个小方框，用黄色方框标出画面现在放大到的位置，方便在高倍数下找准目标。");
                items.add(new SettingsRegistry.Item("zoomFrame", "变焦放大取景框", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "10", opts, notes, "", false, false, "viewfinder", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("live", "边变焦边切镜头", "三颗镜头（超广角 / 主摄 / 长焦）同时开着，跨过倍数时画面柔和淡入淡出，不黑屏；到长焦倍数（如 3×）就换长焦。手机不让同时开多颗时，自动改为「定格 → 换镜头 → 淡入」", "边变焦边切", false));
                opts.add(new SettingsRegistry.Option("after", "数码接力切镜头", "0.5× 时点长焦倍数（≥5×）：不经主摄，先用超广角数码放大到位，停稳 1 秒再淡入长焦；在长焦点 0.5×：先开超广角（对应倍数）再数码缩回。每次都「预热 → 交换画面」，不黑屏，允许少量等待", "数码接力", false));
                opts.add(new SettingsRegistry.Option("orig", "原版（澄影 23）", "超广角 ↔ 主摄的切换方式原封不动，不预热；长焦在变焦停住后才切", "原版", false));
                List<String> notes = new ArrayList<String>();
                notes.add("长焦从 10× 起使用：3×~10× 之间用主摄数码放大。三种方式都能用上长焦。\n手机允许同时开多颗镜头时，「边变焦边切」和「数码接力」用「预热」瞬间换画面；iPhone 通常一次只能开一颗镜头，这时三种方式的长焦都是「变焦停住后：定格画面 → 换镜头 → 淡入」，变焦途中不会去开摄像头。往更广的方向变焦（例如 30× 退回 2×）时长焦拍不到，会立刻换。");
                items.add(new SettingsRegistry.Item("lensSwitch", "倍率镜头切换", "等倍率镜头切换功能完成后开放，下面三种切换方式都还没有接入", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "orig", opts, notes, "", false, false, "arrow.right.to.line", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "变焦到超广角 / 主摄 / 长焦（10× 起）的倍数时怎么换镜头。三颗镜头会提前同时打开待命，多耗一点电；4K 或手机不支持同时开多颗镜头时自动不启用。", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("30", "不扩充（最高 30×）", "原来的范围", "最高 30×", false));
                opts.add(new SettingsRegistry.Option("50", "扩充到 50×", "超出镜头本身的部分为数码放大", "扩充到 50×", false));
                opts.add(new SettingsRegistry.Option("80", "扩充到 80×", "画质会下降，但变焦动作保持顺滑", "扩充到 80×", false));
                opts.add(new SettingsRegistry.Option("100", "扩充到 100×", "最大范围，数码放大最多", "扩充到 100×", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("zoomRange", "变焦倍率扩充", "", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "30", opts, notes, "", true, false, "plus.magnifyingglass", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("变焦倍率扩充", "选择转盘、双指和滚轮变焦的最高倍数。超过镜头本身的部分是数码放大。", items));
        }
        return new SettingsRegistry.Collection("zoom", "变焦与镜头", SettingsRegistry.K_PAGE, "变焦 · 镜头 · 倍率扩充", "变焦与镜头的设置还在逐项适配中，暂时都不可用。", "plus.magnifyingglass", groups);
    }

    static SettingsRegistry.Collection build_pano() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("soft", "柔和羽化（推荐）", "接缝处按离画面中心远近柔和过渡，整体自然、没有硬接缝（第一版的效果）", "柔和羽化", false));
                opts.add(new SettingsRegistry.Option("detail", "细节优先（两频段）", "低频柔和、细节取最近一张，近处物体更少重影，但接缝处可能出现切割线", "细节优先", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("panoBlend", "360° 全景拼接融合", "等全景拍摄功能完成后开放（全景拍摄暂未加入）", SettingsRegistry.T_RADIO, SettingsRegistry.SC_BOTH, SettingsRegistry.S_SOON, "soft", opts, notes, "", true, false, "waveform.path", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("360° 全景拼接融合", "", items));
        }
        return new SettingsRegistry.Collection("pano", "全景", SettingsRegistry.K_PAGE, "拼接融合", "全景拍摄暂未加入，先保留设置入口。", "pano", groups);
    }

    static SettingsRegistry.Collection build_device() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("cams", "摄像头检测", "列出这台设备的所有摄像头、是否支持多摄同时工作", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "cams", false, false, "camera", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("设备", "", items));
        }
        return new SettingsRegistry.Collection("device", "设备与检测", SettingsRegistry.K_PAGE, "摄像头检测", "", "camera", groups);
    }

    static SettingsRegistry.Collection build_dual() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("3", "3 秒", "", "3 秒", false));
                opts.add(new SettingsRegistry.Option("5", "5 秒", "", "5 秒", false));
                opts.add(new SettingsRegistry.Option("6", "6 秒", "", "6 秒", false));
                opts.add(new SettingsRegistry.Option("8", "8 秒", "", "8 秒", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("liveDur", "实况时长", "从按下快门开始录", SettingsRegistry.T_SEG, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "3", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("0", "动态照片（单文件）", "", "动态照片（单文件）", false));
                opts.add(new SettingsRegistry.Option("1", "照片 + 视频分开", "", "照片 + 视频分开", false));
                List<String> notes = new ArrayList<String>();
                notes.add("安卓没有苹果那种实况照片：动态照片 = 一张 JPEG 里内嵌从按下快门开始的短视频，Google 相册等支持动态照片的相册可播放；没有快门前 1.5 秒。");
                items.add(new SettingsRegistry.Item("liveStill", "实况保存方式", "", SettingsRegistry.T_SEG, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("liveTimer", "实况计时显示", "拍实况时在取景框左上角显示已经拍到第几秒、有没有拍完", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "1", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("full", "片门全开", "前后摄像头都用 4:3 最大规格，成品 3:4；帧率尽量保持所选值", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, true, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("fullRate", "满帧画面运行录制", "开：手机发热也不降帧，按你选的帧率显示和录制（可能更烫、更耗电）；关：过热时自动降到 30 帧保护手机", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("0", "无", "", "无", false));
                opts.add(new SettingsRegistry.Option("1", "白色", "", "白色", false));
                opts.add(new SettingsRegistry.Option("2", "黑色", "", "黑色", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("border", "小窗边框", "", SettingsRegistry.T_SEG, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "0", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("mirror", "前置画面镜像", "", SettingsRegistry.T_SWITCH, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "1", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "", items));
        }
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                opts.add(new SettingsRegistry.Option("30", "30 帧", "", "30 帧", false));
                opts.add(new SettingsRegistry.Option("60", "60 帧", "", "60 帧", false));
                opts.add(new SettingsRegistry.Option("120", "120 帧", "", "120 帧", false));
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("pipDragFps", "小窗滑动帧率", "拖动双摄小窗时的画面刷新帧率；越高越顺滑，也更耗电。受屏幕刷新率和前置摄像头帧率限制，120 帧不一定能达到。", SettingsRegistry.T_SEG, SettingsRegistry.SC_DUAL, SettingsRegistry.S_LIVE, "60", opts, notes, "", false, false, "", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("", "说明：多摄同时工作时系统不提供 RAW；动态照片声音跟随「录像时录制声音」。", items));
        }
        return new SettingsRegistry.Collection("dual", "双摄设置", SettingsRegistry.K_PAGE, "实况 · 片门 · 小窗 · 镜像", "这里的设置只作用于双摄（前后摄像头同时拍摄）。单摄的实况设置在「拍照与实况」里。", "rectangle.on.rectangle", groups);
    }

    static SettingsRegistry.Collection build_support() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("donate", "赞赏", "支持作者", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "donate", false, false, "heart", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("支持", "", items));
        }
        return new SettingsRegistry.Collection("support", "支持", SettingsRegistry.K_PAGE, "赞赏", "", "heart", groups);
    }

    static SettingsRegistry.Collection build_about() {
        List<SettingsRegistry.Group> groups = new ArrayList<SettingsRegistry.Group>();
        {
            List<SettingsRegistry.Item> items = new ArrayList<SettingsRegistry.Item>();
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("log", "故障报错日志", "", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "log", false, false, "doc.text", 0, 0));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("version", "版本更新", "{version}", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "version", false, false, "arrow.down.to.line", 0, 0));
            }
            {
                List<SettingsRegistry.Option> opts = new ArrayList<SettingsRegistry.Option>();
                List<String> notes = new ArrayList<String>();
                items.add(new SettingsRegistry.Item("about", "关于澄影", "", SettingsRegistry.T_LINK, SettingsRegistry.SC_BOTH, SettingsRegistry.S_LIVE, "", opts, notes, "about", false, false, "info.circle", 0, 0));
            }
            groups.add(new SettingsRegistry.Group("关于", "", items));
        }
        return new SettingsRegistry.Collection("about", "关于", SettingsRegistry.K_INLINE, "", "", "", groups);
    }

    static List<SettingsRegistry.Collection> buildAll() {
        List<SettingsRegistry.Collection> all = new ArrayList<SettingsRegistry.Collection>();
        all.add(build_account());
        all.add(build_appearance());
        all.add(build_shoot());
        all.add(build_photo());
        all.add(build_video());
        all.add(build_palette());
        all.add(build_zoom());
        all.add(build_pano());
        all.add(build_device());
        all.add(build_dual());
        all.add(build_support());
        all.add(build_about());
        return all;
    }

    /** 只有存储键、没有设置页入口的键：{id, 默认值} */
    static final String[][] EXTRA_DEFAULTS = {
        {"logOn", "1"},
    };
}
