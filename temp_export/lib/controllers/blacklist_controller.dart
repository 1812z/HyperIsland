import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _channel = MethodChannel('io.github.hyperisland/test');
const kPrefAppBlacklist = 'pref_app_blacklist';

class AppInfo {
  final String packageName;
  final String appName;
  final bool isSystem;

  const AppInfo({
    required this.packageName,
    required this.appName,
    this.isSystem = false,
  });
}

class BlacklistController extends ChangeNotifier {
  List<AppInfo> _allApps = [];
  List<AppInfo> _sortedApps = [];
  Set<String> blacklistedPackages = {};
  bool loading = true;
  String _searchQuery = '';
  bool showSystemApps = false;

  static const _gamePresets = {
    'com.miHoYo.Yuanshen', // 原神
    'com.miHoYo.GenshinImpact', // 原神国际服
    'com.miHoYo.ys.bilibili', // 原神B服
    'com.kurogame.mingchao', // 鸣潮
    'com.kurogame.wutheringwaves.global', // 鸣潮国际服
    'com.miHoYo.hkrpg', // 星穹铁道
    'com.miHoYo.hkrpg.bilibili', // 星穹铁道B服
    'com.HoYoverse.hkrpgoversea', // 星穹铁道国际服
    'com.tencent.tmgp.sgame', // 王者荣耀
    'com.tencent.tmgp.sgamece', // 王者荣耀体验服
    'com.garena.game.kgtw', // 传说对决
    'com.tencent.lolm', // 英雄联盟手游
    'com.levelinfinite.sgameGlobal', // 王者荣耀国际服 (Honor of Kings)
    'com.levelinfinite.sgameGlobal.midaspay', // Honor of Kings MidasPay
    'com.tencent.tmgp.pubgmhd', // 和平精英
    'com.tencent.tmgp.pubgmhdce', // 和平精英体验服
    'com.tencent.ig', // PUBG Mobile
    'com.pubg.imobile', // Battlegrounds Mobile India
    'com.pubg.krmobile', // PUBG Mobile (KR/JP)
    'com.rekoo.pubgm', // PUBG Mobile (CN/Global variant)
    'com.vng.pubgmobile', // PUBG Mobile (VN)
    'com.tencent.tmgp.speedmobile', // QQ飞车
    'com.garena.game.fctw', // 极速领域 (Garena QQ Speed)
    'com.dw.h5yvzr.yt', // 幻塔
    'com.pwrd.hotta.laohu', // 幻塔老虎服
    'com.hottagames.hotta.bilibili', // 幻塔B服
    'com.hottagames.hotta.mi', // 幻塔米服
    'com.activision.callofduty.warzone', // COD Warzone
    'com.tencent.tmgp.cod', // 使命召唤手游
    'com.epicgames.fortnite', // Fortnite
    'com.netease.l22', // 永劫无间
    'com.netease.l22.mi', // 永劫无间米服
    'com.netease.l22.nearme.gamecenter', // 永劫无间OPPO服
    'com.tencent.tmgp.gnyx', // 高能英雄
    'com.netease.party', // 蛋仔派对
    'com.netease.party.nearme.gamecenter', // 蛋仔派对OPPO服
    'com.netease.party.vivo', // 蛋仔派对VIVO服
    'com.netease.party.bilibili', // 蛋仔派对B服
    'com.netease.party.mi', // 蛋仔派对米服
    'com.tencent.tmgp.party', // 蛋仔派对腾讯服
    'com.tencent.letsgo', // 元梦之星
    'com.netease.dwrg', // 第五人格
    'com.netease.dwrg.mi', // 第五人格米服
    'com.tencent.tmgp.dwrg', // 第五人格腾讯服
    'com.netease.dwrg.guopan', // 第五人格果盘服
    'com.netease.dwrg.bili', // 第五人格B服
    'com.netease.dwrg.nearme.gamecenter', // 第五人格OPPO服
    'com.netease.dwrg5.vivo', // 第五人格VIVO服
    'com.netease.idv.googleplay', // 第五人格国际服
    'com.tencent.mf.uam', // 暗区突围
    'com.proximabeta.mf.uamo', // Arena Breakout
    'com.netease.yyslscn', // 燕云十六声
    'com.netease.aceracer', // 王牌竞速
    'com.netease.aceracer.aligames', // 王牌竞速阿里服
    'com.netease.aceracer.nubia', // 王牌竞速努比亚服
    'com.netease.aceracer.vivo', // 王牌竞速VIVO服
    'com.netease.aceracer.mi', // 王牌竞速米服
    'com.netease.aceracer.nearme.gamecenter', // 王牌竞速OPPO服
    'com.netease.aceracer.huawei', // 王牌竞速华为服
    'com.netease.nshm', // 逆水寒手游
    'com.tencent.KiHan', // 火影忍者
    'com.kurogame.haru.hero', // 战双帕弥什
    'com.kurogame.haru.mi', // 战双帕弥什米服
    'com.kurogame.haru.aligames', // 战双帕弥什阿里服
    'com.kurogame.haru.bilibili', // 战双帕弥什B服
    'com.hypergryph.arknights', // 明日方舟
    'tw.txwy.and.arknights', // 明日方舟(台服)
    'com.miHoYo.enterprise.NGHSoD', // 崩坏3
    'com.miHoYo.bh3.mi', // 崩坏3米服
    'com.miHoYo.bh3.bilibili', // 崩坏3B服
    'com.tencent.tmgp.cf', // 穿越火线
    'com.tencent.jkchess', // 金铲铲
    'com.netease.hyxd.mi', // 荒野行动米服
    'com.netease.hyxd.aligames', // 荒野行动阿里服
    'com.netease.hyxd.nearme.gamecenter', // 荒野行动OPPO服
    'com.netease.hyxd.wyzymnqsd_cps', // 荒野行动网易模拟器
    'com.tencent.tmgp.dfm', // 三角洲行动
    'com.proxima.dfm', // Delta Force Mobile
    'com.tencent.tmgp.supercell.clashofclans', // 部落冲突腾讯服
    'com.supercell.clashofclans', // 部落冲突
    'com.hermes.h1game', // 航海王热血航线
    'com.hermes.h1game.m4399', // 航海王热血航线4399服
    'com.netease.mrzh', // 明日之后
    'com.netease.mrzh.mi', // 明日之后米服
    'com.netease.mrzh.nearme.gamecenter', // 明日之后OPPO服
    'com.pi.czrxdfirst', // 超自然行动组
    'cn.jj.chess', // JJ象棋
    'cn.jj.chess.mi', // JJ象棋米服
    'com.tencent.tmgp.dnf', // DNF手游
    'com.nexon.mdnf', // DNF手游国际服
    'com.bilibili.azurlane', // 碧蓝航线
    'com.papegames.infinitynikki', // 无限暖暖
    'com.netease.moba', // 决战平安京
    'com.blizzard.wtcg.hearthstone', // 炉石传说
    'com.blizzard.wtcg.hearthstone.cn.dashen', // 炉石传说网易大神版
    'com.blizzard.wtcg.hearthstone.cn.huawei', // 炉石传说华为服
    'com.netease.sky', // 光遇
    'com.tgc.sky.android', // Sky: Children of the Light
    'com.netease.sky.nearme.gamecenter', // 光遇OPPO服
    'com.netease.sky.bilibili', // 光遇B服
    'com.tencent.tmgp.eyou.eygy', // 光遇腾讯服
    'com.netease.sky.mi', // 光遇米服
    'com.netease.sky.m4399', // 光遇4399服
    'com.netease.sky.vivo', // 光遇VIVO服
    'com.netease.sky.huawei', // 光遇华为服
    'com.miHoYo.Nap', // 绝区零
    'com.mihoyo.nap.bilibili', // 绝区零B服
    'com.gameloft.android.GAND.GloftM3HP', // 现代战争3
    'com.aligames.kuang.kybc.aligames', // 狂野飙车9阿里服
    'com.tencent.tmgp.aligames.kybc', // 狂野飙车9腾讯服
    'com.aligames.kuang.kybc.mi', // 狂野飙车9米服
    'com.aligames.kuang.kybc.tap', // 狂野飙车9Tap服
    'com.aligames.kuang.kybc', // 狂野飙车9
    'com.supercell.brawlstars', // 荒野乱斗
    'com.tencent.tmgp.supercell.brawlstars', // 荒野乱斗腾讯服
    'com.mojang.minecraftpe', // Minecraft
    'com.netease.x19', // 我的世界网易版
    'com.tencent.tmgp.wdsj666', // 我的世界腾讯版
    'com.minitech.miniworld', // 迷你世界
    'com.minitech.miniworld.TMobile.mi', // 迷你世界米服
    'com.tencent.tmgp.minitech.miniworld', // 迷你世界腾讯服
    'com.minitech.miniworld.uc', // 迷你世界UC服
    'com.playmini.miniworld', // 迷你世界国际服
    'com.dragonli.projectsnow.lhm', // 尘白禁区
    'com.dragonli.projectsnow.bilibili', // 尘白禁区B服
    'com.ChillyRoom.DungeonShooter', // 元气骑士
    'com.Sunborn.SnqxExilium', // 少女前线
    'com.sunborn.snqxexilium.glo', // 少女前线国际服
    'com.tencent.tmgp.supercell.clashroyale', // 皇室战争腾讯服
    'com.supercell.clashroyale', // 皇室战争
    'com.netease.race', // 巅峰极速
    'com.netease.race.ua', // 巅峰极速海外版
    'com.netease.dfjs', // 巅峰极速
    'com.netease.dfjs.aligames', // 巅峰极速阿里服
    'com.netease.dfjs.mi', // 巅峰极速米服
    'com.netease.onmyoji', // 阴阳师
    'com.netease.onmyoji.vivo', // 阴阳师VIVO服
    'com.netease.onmyoji.wyzymnqsd_cps', // 阴阳师网易模拟器
    'com.netease.onmyoji.bili', // 阴阳师B服
    'com.netease.onmyoji.mi', // 阴阳师米服
    'com.axlebolt.standoff2.huawei', // 对峙2华为服
    'com.axlebolt.standoff2', // 对峙2
    'com.roblox.client', // Roblox
    'com.sofunny.Sausage', // 香肠派对
    'com.ztgame.bob', // 球球大作战
    'com.tencent.tmgp.WePop', // 跑跑卡丁车
    'com.hermes.p6game', // 晶核
    'com.hermes.p6game.mi', // 晶核米服
    'com.hermes.p6game.aligames', // 晶核阿里服
    'com.tencent.nikke', // 胜利女神Nikke
    'com.gamamobi.nikke', // 胜利女神Nikke(台服)
    'com.proximabeta.nikke', // 胜利女神Nikke(国际服)
    'com.yingxiong.heroo.nearme.gamecenter', // 王牌战争
    'com.bf.sgs.hdexp', // 三国杀
    'com.bf.sgs.mi', // 三国杀米服
    'com.bf.sgs.hdexp.m4399', // 三国杀4399服
    'com.humo.yqqsqz.yw', // 元气骑士前传
    'com.humo.yqqsqz.hykb', // 元气骑士前传好游快爆版
    'com.humo.yqqsqz.bilibili', // 元气骑士前传B服
    'com.humo.yqqsqz.mi', // 元气骑士前传米服
    'com.tencent.tmgp.codev', // 无畏契约
    'com.idreamsky.klbqm', // 卡拉彼丘
    'com.bilibili.star.bili', // BanG Dream! B服
    'jp.co.craftegg.band', // BanG Dream! (JP)
    'com.bushiroad.en.bangdreamgbp', // BanG Dream! (EN)
    'net.gamon.bdTW', // BanG Dream! (TW)
    'com.netease.newspike', // 代号血战 (Blood Strike)
    'com.Nekootan.kfkj.android', // 开放空间
    'com.Nekootan.kfkj.yhlm.aligames', // 开放空间阿里服
    'com.tencent.tmgp.Nekootan.kfkj.yhlm', // 开放空间腾讯服
    'com.Nekootan.kfkj.yhlm.mi', // 开放空间米服
    'com.netease.yhtj', // 萤火突击
    'com.netease.yhtj.m4399', // 萤火突击4399服
    'com.netease.yhtj.gg', // 萤火突击海外版
    'com.netease.yhtj.aligames', // 萤火突击阿里服
    'com.netease.yhtj.mi', // 萤火突击米服
    'com.hermes.mk', // 初音未来: 缤纷舞台
    'com.sega.pjsekai', // Project SEKAI (JP)
    'com.hermes.mk.asia', // Project SEKAI (Asia)
    'com.hermes.mk.bilibili', // Project SEKAI B服
    'com.nd.he', // 英魂之刃
    'com.nd.he.mi', // 英魂之刃米服
    'com.nd.hoa.aligames', // 英魂之刃阿里服
    'com.nd.he.gamename.m4399', // 英魂之刃4399服
    'com.tencent.tmgp.coslegend', // 英魂之刃腾讯服
    'com.RoamingStar.BlueArchive', // 蔚蓝档案
    'com.nexon.bluearchive', // Blue Archive (Global)
    'com.YostarJP.BlueArchive', // Blue Archive (JP)
    'com.humble.SlayTheSpire', // 杀戮尖塔
    'com.sqw.cc.sgdbz_ta', // 时光大爆炸
    'com.tencent.nfsonline', // 极品飞车: 集结
    'com.neowizgames.game.browndust2', // 棕色塵埃2
    'com.tencent.pocket', // 腾讯桌球
    'com.chillyroom.zhmr.yw', // 战魂铭人
    'com.chillyroom.zhmr.gp', // 战魂铭人Google Play版
    'com.chillyroom.zhmr.mi', // 战魂铭人米服
    'com.chillyroom.zhmr.aligames', // 战魂铭人阿里服
    'com.ztgame.yyzy', // 月圆之夜
    'com.ztgame.yyzy.aligames', // 月圆之夜阿里服
    'com.tencent.tmgp.sskgame', // 圣斗士星矢
    'com.tencent.YiRen', // 异人之下
    'com.netease.tom', // 猫和老鼠
    'com.netease.tom.mi', // 猫和老鼠米服
    'com.tencent.tmgp.NBA', // 最强NBA
    'com.netease.yzs', // 影之诗
    'jp.co.cygames.ShadowverseWorldsBeyond', // Shadowverse: Worlds Beyond
    'com.bairimeng.dmmdzz', // 逃跑吧少年
    'com.bairimeng.dmmdzz.mi', // 逃跑吧少年米服
    'com.bairimeng.dmmdzz.betazone', // 逃跑吧少年开发者版
    'com.bairimeng.dmmdzz.m4399', // 逃跑吧少年4399服
    'com.bairimeng.dmmdzz.honor', // 逃跑吧少年荣耀服
    'com.bairimeng.dmmdzz.vivo', // 逃跑吧少年VIVO服
    'com.tencent.tmgp.bairimeng.dmmdzz', // 逃跑吧少年腾讯服
    'com.tencent.tmgp.djsy', // 妄想山海
    'com.qqgame.hlddz', // 欢乐斗地主
    'com.guigugame.guigubahuang', // 鬼谷八荒
    'com.gaijingames.wtm', // 战争雷霆
    'com.xindong.torchlight', // 火炬之光：无限
    'com.tencent.tmgp.qqx5', // QQ炫舞
    'com.tencent.game.rhythmmaster', // 节奏大师
    'com.netease.allstar', // 全明星街球派对
    'com.tencent.nba2kx', // 美职篮全明星
    'com.bandainamcoent.idolmaster_gakuen', // 学园偶像大师
    'com.lilithgames.solarland.android.cn', // 远光84
    'com.miraclegames.farlight84', // Farlight 84 (Global)
    'com.pkwan.op.toufang.dy', // 航海王：燃烧意志
    'com.tencent.tmgp.pkwan.op', // 航海王：燃烧意志腾讯服
    'com.tungsten.fcl', // Fold Craft Launcher
    'com.bilibili.heaven', // 炽焰天穹
    'com.heavenburnsred.kbinstaller', // 绯染天空安装器
    'com.heavenburnsred', // 绯染天空 (JP)
    'com.hero.sm.bz', // 创造与魔法
    'com.hero.sm.android.hero', // 创造与魔法英雄服
    'com.hero.sm.mi', // 创造与魔法米服
    'com.hero.sm.aligames', // 创造与魔法阿里服
    'com.hero.sm.huawei', // 创造与魔法华为服
    'com.tencent.tmgp.sm', // 创造与魔法腾讯服
    'com.nordcurrent.flyingfever', // 飞机大厨
    'com.tencent.tmgp.fmgame', // 最终幻想14水晶世界
    'com.yinhan.hunter.yh', // 时空猎人
    'com.yinhan.hunter.mi', // 时空猎人米服
    'com.yinhan.hunter.uc', // 时空猎人UC服
    'com.yinhan.hunter.huawei', // 时空猎人华为服
    'com.yinhan.hunter.tx', // 时空猎人腾讯服
    'com.yinhan.hunter.qihoo', // 时空猎人360服
    'com.netease.sdsbq', // 射雕
    'com.netease.sdsbq.mi', // 射雕米服
    'com.netease.sdsbq.huawei', // 射雕华为服
    'com.TeamCherry.HollowKnight', // 空洞骑士
    'com.tencent.tmgp.yslzm', // 以闪亮之名
    'com.zulong.yslzm.mi', // 以闪亮之名米服
    'com.archosaur.sea.yslzm.gp', // 以闪亮之名国际服
    'com.soulgamechst.majsoul', // 雀魂麻将
    'com.tapblaze.pizzabusiness', // 可口的比萨
    'com.studiowildcard.arkuse', // 方舟生存进化
    'com.duoyi.m2m1', // 枪火重生
    'com.k7k7.goujihd', // 多乐够级
    'com.k7k7.goujihd.mi', // 多乐够级米服
    'com.k7k7.goujihd.huawei', // 多乐够级华为服
    'com.hero.dna.gf', // 二重螺旋
    'com.zane.stardewvalley', // 星露谷物语
    'abc.ningban.gameloades', // 星露谷物语(其他版)
    'com.tencent.nrc', // 洛克王国世界
    'com.xuejing.smallfish.official', // 欢乐钓鱼大师
    'com.xuejing.smallfish.mi', // 欢乐钓鱼大师米服
    'com.tencent.tmgp.hldyds', // 欢乐钓鱼大师腾讯服
    'com.xuejing.smallfish.huawei', // 欢乐钓鱼大师华为服
    'gg.com.fishgame.fishon', // Fishing Master
    'com.dfjz.moba', // 决胜巅峰
    'com.mobile.legends', // Mobile Legends: Bang Bang
    'gg.com.mobile.legends.lite', // MLBB Lite
    'com.dfjz.moba.aligames', // 决胜巅峰阿里服
    'com.dfjz.moba.mi', // 决胜巅峰米服
    'com.tencent.tmgp.cfxf', // 超凡先锋
    'com.netease.cfxf.huawei', // 超凡先锋华为服
    'com.netease.cfxf.mi', // 超凡先锋米服
    'com.rockstargames.rdr', // 荒野大镖客
    'com.bilibili.snake', // 坎特伯雷公主与骑士
    'com.tencent.tmgp.bilibili.snake', // 坎公骑冠剑腾讯服
    'com.bilibili.snake.mi', // 坎公骑冠剑米服
    'com.bilibili.snake.aligames', // 坎公骑冠剑阿里服
    'com.bilibili.snake.vivo', // 坎公骑冠剑VIVO服
    'com.bilibili.snake.huawei', // 坎公骑冠剑华为服
    'com.DefaultCompany.heimalouxiangsutest', // 黑神话悟空(像素版)
    'net.pvz.pgvz.zbcteam', // 植物大战僵尸(娘版)
    'com.Lonerangerix.ArrowGame', // 剑剑剑
    'com.jurassic.world.the.cursed.isle.dinosaurs.carnivores.dino.hunter.dinos.online.trex.tyrannosaurus.simulator', // 诅咒之岛
    'jp.konami.pesam', // eFootball
    'com.t2ksports.myteam2k26v2', // NBA 2K26 MyTEAM
    'com.t2ksports.myteam2k25', // NBA 2K25 MyTEAM
    'com.tencent.tmgp.nz', // 逆战:未来
    'com.hypergryph.endfield', // 明日方舟终末地
    'com.gryphline.endfield.gp', // Arknights: Endfield (Global)
    'com.LoongCharm.infinityworld', // 开门就是仙侠世界
    'com.tencent.rmcn', // 失控进化
    'com.digitalextremes.warframemobile', // Warframe Mobile
    'sh.ppy.osulazer', // osu! (lazer)
    'com.sybogames.subway.surfers.game', // Subway Surfers City
    'com.netease.harrypotter', // 哈利波特：魔法觉醒
    'com.netease.harrypotter.mi', // 哈利波特米服
    'com.netease.harrypotter.vivo', // 哈利波特VIVO服
    'com.netease.harrypotter.nearme.gamecenter', // 哈利波特OPPO服
    'com.tencent.tmgp.harrypotter', // 哈利波特腾讯服
    'com.tencent.tmgp.supercell.boombeach', // 海岛奇兵腾讯服
    'com.cipaishe.wuhua.bilibili', // 物华弥新B服
    'com.supercell.boombeach', // 海岛奇兵国际服
  };

  BlacklistController() {
    _load();
  }

  final _iconCache = <String, Uint8List>{};

  Future<Uint8List?> getAppIcon(String packageName) async {
    if (_iconCache.containsKey(packageName)) return _iconCache[packageName];
    try {
      final bytes = await _channel.invokeMethod('getAppIcon', {
        'packageName': packageName,
      });
      if (bytes is Uint8List) {
        _iconCache[packageName] = bytes;
        return bytes;
      } else if (bytes is List) {
        final data = Uint8List.fromList(bytes.cast<int>());
        _iconCache[packageName] = data;
        return data;
      }
    } catch (_) {}
    return null;
  }

  void _resort() {
    _sortedApps = List<AppInfo>.from(_allApps)
      ..sort((a, b) {
        final aOn = blacklistedPackages.contains(a.packageName);
        final bOn = blacklistedPackages.contains(b.packageName);
        if (aOn != bOn) return aOn ? -1 : 1;
        return a.appName.compareTo(b.appName);
      });
  }

  List<AppInfo> get filteredApps {
    final q = _searchQuery.toLowerCase();
    Iterable<AppInfo> source = showSystemApps
        ? _sortedApps
        : _sortedApps.where(
            (a) => !a.isSystem || blacklistedPackages.contains(a.packageName),
          );
    if (q.isNotEmpty) {
      source = source.where(
        (a) =>
            a.appName.toLowerCase().contains(q) ||
            a.packageName.toLowerCase().contains(q),
      );
    }
    return source is List<AppInfo> ? source : source.toList();
  }

  Future<void> refresh() => _load();

  Future<void> _load() async {
    loading = true;
    notifyListeners();

    try {
      final prefs = await SharedPreferences.getInstance();
      final csv = prefs.getString(kPrefAppBlacklist) ?? '';
      blacklistedPackages = csv.isEmpty
          ? {}
          : csv.split(',').where((s) => s.isNotEmpty).toSet();

      final rawList =
          await _channel.invokeMethod<List<dynamic>>('getInstalledApps', {
            'includeSystem': true,
          }) ??
          [];
      const excludedPackages = {
        'com.android.providers.downloads',
        'com.xiaomi.android.app.downloadmanager',
        'com.android.systemui',
      };
      _allApps = rawList
          .map((raw) {
            final map = Map<String, dynamic>.from(raw as Map);
            return AppInfo(
              packageName: map['packageName'] as String,
              appName: map['appName'] as String,
              isSystem: map['isSystem'] as bool? ?? false,
            );
          })
          .where((a) => !excludedPackages.contains(a.packageName))
          .toList();
      _resort();
    } catch (e) {
      debugPrint('BlacklistController._load error: $e');
    }

    loading = false;
    notifyListeners();
  }

  Future<void> setBlacklisted(String packageName, bool enabled) async {
    if (enabled) {
      blacklistedPackages.add(packageName);
    } else {
      blacklistedPackages.remove(packageName);
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAppBlacklist, blacklistedPackages.join(','));
    notifyListeners();
  }

  void setSearch(String query) {
    _searchQuery = query;
    _resort();
    notifyListeners();
  }

  void setShowSystemApps(bool value) {
    showSystemApps = value;
    _resort();
    notifyListeners();
  }

  Future<void> enableAll() async {
    for (final a in filteredApps) {
      blacklistedPackages.add(a.packageName);
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAppBlacklist, blacklistedPackages.join(','));
    notifyListeners();
  }

  Future<void> disableAll() async {
    for (final a in filteredApps) {
      blacklistedPackages.remove(a.packageName);
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAppBlacklist, blacklistedPackages.join(','));
    notifyListeners();
  }

  Future<int> applyGamePreset() async {
    int addedCount = 0;
    for (final app in _allApps) {
      if (_gamePresets.contains(app.packageName) &&
          !blacklistedPackages.contains(app.packageName)) {
        blacklistedPackages.add(app.packageName);
        addedCount++;
      }
    }
    if (addedCount > 0) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(kPrefAppBlacklist, blacklistedPackages.join(','));
      _resort();
      notifyListeners();
    }
    return addedCount;
  }
}
