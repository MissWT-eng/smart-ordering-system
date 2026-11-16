-- MySQL dump 10.13  Distrib 8.0.41, for Win64 (x86_64)
--
-- Host: localhost    Database: smart_ordering
-- ------------------------------------------------------
-- Server version	8.0.41

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `smart_ordering`
--

/*!40000 DROP DATABASE IF EXISTS `smart_ordering`*/;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `smart_ordering` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `smart_ordering`;

--
-- Table structure for table `dish`
--

DROP TABLE IF EXISTS `dish`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dish` (
  `dish_number` bigint NOT NULL AUTO_INCREMENT COMMENT '菜品号',
  `dish_name` varchar(100) NOT NULL COMMENT '菜品名',
  `dish_status` enum('available','unavailable') DEFAULT 'available' COMMENT '今日是否可制作',
  `dish_introduction` text COMMENT '菜品介绍（用于RAG检索）',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `category` enum('meat','vegetable','drink','soup') DEFAULT 'vegetable' COMMENT '菜品分类',
  `spicy_level` enum('none','mild','medium','high') DEFAULT 'none',
  PRIMARY KEY (`dish_number`),
  UNIQUE KEY `dish_name` (`dish_name`),
  KEY `idx_category` (`category`)
) ENGINE=InnoDB AUTO_INCREMENT=83 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dish`
--

LOCK TABLES `dish` WRITE;
/*!40000 ALTER TABLE `dish` DISABLE KEYS */;
INSERT INTO `dish` VALUES (1,'宫保鸡丁豪华版','available','经典川菜',32.50,'meat','medium'),(2,'麻辣香锅','unavailable','麻辣鲜香，微辣口感，配菜丰富',39.90,'meat','high'),(3,'水煮牛肉','available','川菜经典，嫩滑牛里脊切片配黄豆芽垫底，浇滚烫辣椒红油，麻辣鲜香、辣度偏高，牛肉嫩滑入味，重口味下饭首选，不带汤水',58.00,'meat','high'),(4,'红烧肉','available','本帮家常菜，五花肉慢炖至软糯脱骨，酱香浓郁偏甜口，肥而不腻，带浓稠汤汁，不辣，适合配米饭，老少皆宜',46.00,'meat','none'),(5,'糖醋里脊','available','外酥里嫩的炸里脊，裹酸甜糖醋汁，口感酥脆多汁，酸甜开胃完全不辣，小孩子和不吃辣的人最爱',38.00,'meat','none'),(6,'清蒸鲈鱼','available','粤式做法，鲜活鲈鱼清蒸，只用姜丝葱丝和豉油提鲜，鱼肉细嫩清淡、原汁原味，几乎没有油，不辣，适合养生和减脂人群',68.00,'meat','none'),(7,'口水鸡','available','川式凉菜，白斩鸡浇红油花椒料汁，麻辣鲜香带点回甜，鸡肉紧实，冷食开胃解腻，夏天吃很爽，辣度中等偏上',36.00,'meat','medium'),(8,'黑椒牛柳','available','西式做法，牛柳配彩椒洋葱快炒，黑胡椒香气浓郁，微辣，牛肉嫩滑多汁，口味偏咸鲜，下饭又不算太重口',52.00,'meat','mild'),(9,'香辣虾','available','鲜虾开背去线，与干辣椒花椒同炒，香辣酥脆、越吃越上瘾，辣度偏高，壳酥可连壳吃，配啤酒最佳',78.00,'meat','high'),(10,'白切鸡','available','粤式冷盘，整鸡浸熟后冰镇，皮爽肉滑、清淡不腻，只有淡淡咸香完全不辣，蘸姜葱油或沙姜酱油食用',42.00,'meat','none'),(11,'麻婆豆腐','available','川菜家常，嫩豆腐配肉末烧制，麻辣烫口、豆瓣香浓，勾薄芡让汤汁裹住豆腐，拌饭一绝，辣度中等',26.00,'vegetable','medium'),(12,'蒜蓉西兰花','available','西兰花焯水后与蒜末清炒，口感爽脆清甜，少油少盐，完全不辣，低卡轻食，健身和减脂人群首选',22.00,'vegetable','none'),(13,'干锅花菜','available','有机花菜配五花肉片和干辣椒爆炒，干香微辣、锅气十足，花菜吸满油脂越嚼越香，重口味下饭菜',32.00,'vegetable','mild'),(14,'地三鲜','available','东北名菜，土豆、茄子、青椒过油后同烧，油润咸香、口感软糯，酱汁浓稠，完全不辣，家常味十足',28.00,'vegetable','none'),(15,'凉拌黄瓜','available','嫩黄瓜拍碎后加蒜末香醋和少许辣椒油拌匀，爽脆多汁、清爽解腻，微辣可以要求不放辣，夏天开胃小菜',16.00,'vegetable','mild'),(16,'醋溜土豆丝','available','土豆切细丝猛火快炒，加陈醋提酸，口感爽脆酸辣，家常下饭，辣度偏轻，酸味开胃',18.00,'vegetable','mild'),(17,'手撕包菜','available','包菜手撕后与干辣椒蒜片爆炒，脆嫩入味、微微辣香，锅气足，便宜又下饭的经典素菜',20.00,'vegetable','mild'),(18,'冰镇酸梅汤','available','乌梅山楂陈皮熬煮后冰镇，酸甜生津、清爽解腻，冰饮，吃完辣菜来一杯特别解辣',12.00,'drink','none'),(19,'柠檬气泡水','available','现切柠檬配苏打气泡水，加冰块，清爽微酸、无糖零卡，冰饮，解腻解辣，适合控糖人群',15.00,'drink','none'),(20,'热豆浆','available','现磨黄豆豆浆，温热醇香、微甜，暖胃易消化，不含咖啡因，早餐或配清淡菜都合适',8.00,'drink','none'),(21,'鲜榨橙汁','available','新鲜橙子现榨，果香浓郁、酸甜可口，富含维C，常温或加冰可选，清爽解腻',18.00,'drink','none'),(22,'葱姜焖鸡','available','选用带骨鸡块，先煎出香气，再与大葱、姜片和少量酱油一同焖煮。鸡肉咸鲜，酱汁带有明显葱姜香，收汁后上桌，不添加辣椒。',48.00,'meat','none'),(23,'黄焖鸡块','available','鸡块与香菇、土豆一同焖制，加入少量青红辣椒提味，最后将汤汁略微收浓。口味酱香咸鲜，带轻微辣感，鸡肉和配菜都裹有酱汁。',42.00,'meat','mild'),(24,'干煸肥肠','available','肥肠处理后先卤煮再煸炒，配芹菜、干辣椒和花椒炒出香气。口感外层略脆、内里有韧性，味道咸香麻辣，辣度为中辣，适合偏重口味的选择。',56.00,'meat','medium'),(25,'孜然羊肉','available','羊肉切片后与洋葱快速翻炒，加入孜然粒、孜然粉和少量辣椒粉。羊肉带有浓郁孜然香，洋葱保留些许脆口，整体咸香微辣，不带厚重汤汁。',68.00,'meat','mild'),(26,'剁椒鱼头','available','鱼头铺上剁椒、蒜末和姜丝后蒸制，出锅时淋少量热油激香。鱼肉细嫩，蒸汁融合剁椒的酸香与咸鲜，辣味较明显，按中辣口味制作。',88.00,'meat','medium'),(27,'椒盐排骨','available','排骨腌味后炸至表面金黄，再与蒜末、葱段和少量干辣椒翻炒，撒入椒盐。入口酥香，骨边肉带嚼劲，味道咸鲜微辣，采用干香做法。',58.00,'meat','mild'),(28,'酱香鸭块','available','鸭肉切块后与姜片、葱段和甜面酱慢火焖煮，待入味后收汁。鸭肉保持有嚼劲的口感，酱香浓郁，咸味中带一点回甜，不放辣椒。',46.00,'meat','none'),(29,'蒜香鸡翅','available','鸡翅以蒜末和酱油腌制后煎熟，出锅前再裹一层蒜香酱汁。表皮略带焦香，鸡肉鲜嫩，整体为咸鲜中略带甜味的口味，不添加辣椒。',38.00,'meat','none'),(30,'酸菜鱼','available','鱼片搭配切碎酸菜、姜片和少量泡椒煮制，鱼骨先煮出汤底，再下鱼片。酸香突出，汤汁咸鲜，鱼片嫩滑，泡椒带来轻微辣感。',72.00,'meat','mild'),(31,'豉汁蒸排骨','available','小块排骨与豆豉、蒜末及少量酱油拌匀后蒸熟，保留蒸出的肉汁。豆豉香与蒜香融合，排骨口感嫩中有嚼劲，味道咸鲜，不放辣椒。',45.00,'meat','none'),(32,'香煎带鱼','available','带鱼切段，以姜葱略作腌味后煎至两面金黄，再加入少量酱油调味。鱼肉细嫩，表面带有煎香，整体咸鲜不辣，食用时需留意鱼刺。',52.00,'meat','none'),(33,'小炒黄牛肉','available','牛肉薄片与小米椒、蒜片和香菜旺火快炒，少量酱油增添咸鲜。牛肉口感嫩滑，椒香与蒜香明显，小米椒用量较足，按特辣口味制作。',66.00,'meat','high'),(34,'川味回锅肉','available','五花肉先煮熟切片，再与蒜苗、豆瓣酱和辣椒一起回锅煸炒。肉片边缘略卷，口感肥瘦相间，豆瓣咸香突出，整体为中辣口味。',48.00,'meat','medium'),(35,'金蒜蒸大虾','available','大虾开背后铺上蒜蓉蒸制，再淋少量蒸鱼豉油，点缀炸香的蒜粒。虾肉紧实鲜甜，蒜香有层次，蒸汁咸鲜，不添加辣椒，保留虾壳上桌。',98.00,'meat','none'),(36,'砂锅牛腩','available','牛腩块与白萝卜、姜片和香料慢炖，待肉软后收成较浓的酱汁，以砂锅盛装。牛肉酱香咸鲜，萝卜吸收肉汁，整体不辣，属于炖肉菜。',128.00,'meat','none'),(37,'香菇油菜','available','香菇切片与小油菜一同清炒，加入蒜末和少量酱油提味，最后略微勾芡。油菜保留脆嫩口感，香菇柔软，味道咸鲜，以菌菇与蔬菜香为主，不放辣椒。',24.00,'vegetable','none'),(38,'番茄炒蛋','available','鸡蛋先炒成柔软蛋块，再与番茄同炒，加入少量盐和糖平衡味道。番茄汁包裹蛋块，口感软嫩，味型酸甜中带咸鲜，不添加辣椒，含鸡蛋。',22.00,'vegetable','none'),(39,'清炒莴笋','available','莴笋去皮切片，配蒜片旺火快炒，以盐调味后出锅。菜品保留莴笋自身的清香和脆口，味道较清淡，汤汁少，不放肉类配料或辣椒。',20.00,'vegetable','none'),(40,'木耳炒山药','available','山药切片与泡发木耳、胡萝卜片一起快炒，加入少量蒜末和盐调味。山药脆中带粉，木耳有弹性，味道清淡咸鲜，不添加肉类或辣椒。',28.00,'vegetable','none'),(41,'干煸四季豆','available','四季豆充分加热至熟后，与蒜末、干辣椒和少量芽菜干煸，炒去多余水分。豆角表皮略皱，口感软中带韧，味道咸香微辣，本菜不放肉末。',26.00,'vegetable','mild'),(42,'酸辣白菜','available','白菜切块后与干辣椒一起旺火翻炒，沿锅边加入米醋，以盐和少量糖调味。菜梗脆嫩，菜叶柔软，酸香与辣味较明显，按中辣口味制作。',18.00,'vegetable','medium'),(43,'家常烧豆腐','available','豆腐煎至表面金黄后，配青椒、木耳和少量豆瓣酱烧制，收汁后上桌。豆腐外层有韧性，内部柔软，味道咸鲜微辣，不加入肉末或肉片。',26.00,'vegetable','mild'),(44,'松仁玉米','available','甜玉米粒与胡萝卜丁、青豆一同翻炒，出锅前加入炒香的松仁，以盐和少量糖调味。玉米清甜，松仁带坚果香，整体不辣，含松仁配料。',30.00,'vegetable','none'),(45,'虎皮青椒','available','选用有明显辣味的青椒，煎至表面起皱，再加入蒜末、酱油和米醋焖炒。青椒柔软多汁，带煎香和酸咸味，整体为中辣，不放肉馅。',24.00,'vegetable','medium'),(46,'清炒藕片','available','莲藕切成薄片，配少量胡萝卜片和葱段快速翻炒，以盐调味。藕片爽脆，保留莲藕的清香，味道咸鲜而清淡，不添加肉类、辣椒或辣椒油。',22.00,'vegetable','none'),(47,'凉拌海带丝','available','海带丝焯熟后放凉，与蒜末、米醋、酱油及少量辣椒油拌匀。口感脆韧，醋香明显，咸鲜中带轻微辣味，作为凉菜上桌，不添加肉类。',16.00,'vegetable','mild'),(48,'芹菜炒香干','available','芹菜切段、香干切条，与蒜片一起快炒，加入少量酱油和盐调味。芹菜有脆口与清香，香干紧实有豆香，口味咸鲜，不放肉丝和辣椒。',24.00,'vegetable','none'),(49,'蒜蓉娃娃菜','available','娃娃菜切瓣后蒸至熟软，铺上炒香的蒜蓉，再淋少量酱油调成的咸鲜汁。菜叶柔软，菜梗保留轻微脆感，蒜香明显，不添加辣椒或肉类。',22.00,'vegetable','none'),(50,'香辣土豆片','available','土豆切片煎熟后，与小米椒、干辣椒和蒜末一同翻炒，撒少量孜然调味。土豆边缘略带焦香，口感软糯，辣味集中明显，按特辣口味制作。',20.00,'vegetable','high'),(51,'葱油拌豆皮','available','豆皮切丝焯熟后沥干，加入葱段熬出的葱油、酱油和少量米醋拌匀。豆皮柔韧，葱香突出，味道咸鲜略带酸味，作为不辣的凉菜上桌。',18.00,'vegetable','none'),(52,'蒜苗炒杏鲍菇','available','杏鲍菇切片后先煎出香气，再与蒜苗、蒜片及少量干辣椒翻炒。菌菇口感厚实，蒜苗清香明显，酱油调出咸鲜味，整体微辣，不放肉片。',28.00,'vegetable','mild'),(53,'清蒸南瓜','available','南瓜去籽后切成厚块，隔水蒸至熟软，以原味上桌，不另加糖浆或酱汁。口感软糯，保留南瓜自身的清甜与香气，不放辣椒，也不搭配肉类。',18.00,'vegetable','none'),(54,'桂花乌梅饮','available','乌梅与山楂片煮出酸香，加入少量冰糖，放凉后以桂花增香。饮品入口酸甜，带乌梅的微微烟香和桂花香，按冷饮供应，不加入辣味配料。',14.00,'drink','none'),(55,'红枣银耳饮','available','银耳泡发后与去核红枣慢煮，加入少量冰糖，煮至汤汁略有稠感后以杯装供应。入口柔滑，能喝到细碎银耳和红枣果肉，甜味温和，不加辣味配料。',16.00,'drink','none'),(56,'蜂蜜柚子茶','available','柚子果酱加入温水搅匀，再调入蜂蜜，以热饮形式供应。入口酸甜，带柚皮的清香和一点果皮苦味，杯中保留少量柚子丝，不含辣味配料。',18.00,'drink','none'),(57,'茉莉冷泡茶','available','茉莉花茶以冷水浸泡后过滤茶叶，冷藏供应，不另加糖或奶。茶汤清透，带有茉莉花香和茶叶本身的轻微涩感，口味清淡，不加入辣味配料。',10.00,'drink','none'),(58,'陈皮普洱茶','available','普洱熟茶与陈皮以热水冲泡，滤出茶叶后供应，不另加糖或奶。茶汤带普洱的醇厚茶香与陈皮的柑橘香，入口略有回甘，不添加辣味配料。',12.00,'drink','none'),(59,'姜枣热饮','available','生姜切片，与去核红枣和少量红糖一起煮制，过滤姜片后热饮供应。红枣香与姜香较明显，入口甜润中带姜的辛香，不加入辣椒或其他辣味调料。',12.00,'drink','none'),(60,'苹果肉桂热饮','available','苹果切片后与肉桂棒一同温煮，加入少量冰糖，保留部分苹果片杯装供应。味道酸甜，带肉桂的木质香料气息，属于热果饮，不加入辣椒。',20.00,'drink','none'),(61,'百香果气泡饮','available','百香果果浆与气泡水混合，加入少量糖浆和冰块，保留可见的百香果籽。入口酸甜，气泡感明显，带热带果香与细小果籽口感，不加入辣味配料。',18.00,'drink','none'),(62,'荔枝苏打水','available','荔枝果汁与苏打水、冰块调和，加入少量荔枝果肉后杯装供应。整体以荔枝的甜香为主，入口带轻盈气泡感，略有果汁酸味，不添加辣味配料。',18.00,'drink','none'),(63,'桂花乌龙奶茶','available','乌龙茶冲泡后与牛奶、少量糖浆调和，以桂花增加香气，冷饮供应。茶香和奶香并存，入口柔和略甜，含牛奶，不加入辣椒或辣味香料。',22.00,'drink','none'),(64,'山楂果饮','available','山楂片加水煮出果味，过滤后加入少量冰糖，放凉后以冷饮供应。味道以酸为主、甜味作衬，带山楂本身的果香与轻微涩感，不加入辣味配料。',14.00,'drink','none'),(65,'瓶装饮用水','available','提供原装密封的瓶装饮用水，按常温供应，不额外加入糖、果汁或其他调味。入口清淡，开封前可核对瓶盖密封情况，具体容量以现场瓶身标示为准。',6.00,'drink','none'),(66,'冬瓜排骨汤','available','排骨焯水后与姜片慢炖，再加入冬瓜煮至软嫩，以盐调味。汤中保留带骨排骨和冬瓜块，肉香与瓜香融合，味道咸鲜清淡，不添加辣椒。',36.00,'soup','none'),(67,'番茄牛腩汤','available','牛腩块炖软后，加入炒过的番茄和少量洋葱继续煮制。汤汁带番茄的自然酸味，牛腩有肉香与软韧口感，以盐调出咸鲜味，不加辣椒。',48.00,'soup','none'),(68,'玉米鸡汤','available','鸡块与玉米段、胡萝卜块和姜片一同慢炖，出锅前以盐调味。汤中鸡肉带鲜香，玉米略甜，整体咸鲜而不浓稠，不使用辣椒或辣椒油。',38.00,'soup','none'),(69,'海带豆腐汤','available','海带切片后与嫩豆腐同煮，加入少量姜丝和盐调味，保留清汤口感。豆腐软嫩，海带有轻微韧性，味道咸鲜，本汤不放肉类或辣椒。',22.00,'soup','none'),(70,'菌菇汤','available','香菇、白玉菇和杏鲍菇切配后煮成汤，加入少量葱段，以盐调味。不同菌菇带来软嫩和弹韧的口感，汤味咸鲜，菌香突出，不添加肉类或辣椒。',28.00,'soup','none'),(71,'紫菜蛋花汤','available','紫菜放入沸水中煮开，缓缓淋入蛋液形成蛋花，加入少量葱花和盐。汤体清爽，蛋花柔软，紫菜香气明显，不放辣椒，含鸡蛋配料。',16.00,'soup','none'),(72,'酸辣汤','available','豆腐丝、木耳丝与鸡蛋同煮，加入米醋、白胡椒和辣椒碎，最后薄芡收汤。入口酸味突出，辣味随后显现，汤体略稠，按中辣口味制作。',26.00,'soup','medium'),(73,'胡椒猪肚汤','available','猪肚处理后切条，与姜片和白胡椒慢煮，保留汤汁和肚条一同上桌。猪肚有嚼劲，汤味咸鲜，白胡椒带来明确但较轻的辛辣感，归为微辣口味。',48.00,'soup','mild'),(74,'萝卜羊肉汤','available','羊肉切块后与白萝卜、姜片一同炖煮，以盐调味，出锅点缀葱花。羊肉软中有韧性，萝卜吸收汤汁，汤味咸鲜并保留羊肉香，不添加辣椒。',58.00,'soup','none'),(75,'鲜虾豆腐汤','available','鲜虾与嫩豆腐、少量姜丝煮成汤，以盐调出咸鲜味，出锅前撒葱花。虾肉紧实，豆腐柔软，汤汁以虾的鲜香为主，不加辣椒，保留虾壳上桌。',42.00,'soup','none'),(76,'丝瓜蛤蜊汤','available','蛤蜊煮至开口后加入丝瓜块和姜丝，煮熟后以少量盐调味。丝瓜软嫩，蛤蜊带海味鲜香，汤体清淡不辣，蛤蜊连壳盛装，上桌时需留意贝壳。',38.00,'soup','none'),(77,'莲藕排骨汤','available','排骨焯水后与莲藕块、姜片慢炖，以盐调味，不另勾芡。莲藕煮后粉糯，排骨保留骨边肉香，汤中带藕香与肉香，味道咸鲜，不放辣椒。',42.00,'soup','none'),(78,'白菜丸子汤','available','猪肉末调成丸子，煮至熟透后加入白菜叶和少量姜丝，最后以盐调味。丸子紧实有弹性，白菜柔软，汤味以肉香和菜香为主，不添加辣椒。',32.00,'soup','none'),(79,'香菇鸡丸汤','available','鸡肉末与切碎香菇调成丸子，放入汤中煮熟，再加入香菇片和葱花。鸡丸口感细嫩，菌菇香气明显，整体为清淡咸鲜的汤品，不放辣椒。',34.00,'soup','none'),(80,'金汤酸菜肥牛汤','available','用南瓜泥调出金色汤底，加入酸菜、金针菇和少量泡椒，最后烫熟肥牛片。汤味酸香咸鲜，牛肉柔嫩，泡椒带来轻微辣感，按微辣口味制作。',56.00,'soup','mild'),(81,'香辣鱼片汤','available','鱼片与豆芽同煮，加入小米椒、干辣椒和花椒，出锅前淋少量热油激香。汤汁咸鲜麻辣，鱼片嫩滑，辣椒用量较足，按特辣口味制作。',46.00,'soup','high');
/*!40000 ALTER TABLE `dish` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `operation_log`
--

DROP TABLE IF EXISTS `operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator` varchar(50) NOT NULL COMMENT '操作人',
  `operation_type` varchar(20) NOT NULL COMMENT '操作类型 INSERT/UPDATE/DELETE',
  `operation_content` text COMMENT '修改内容详情',
  `operation_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`log_id`),
  KEY `idx_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商户操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `operation_log`
--

LOCK TABLES `operation_log` WRITE;
/*!40000 ALTER TABLE `operation_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `operation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_item`
--

DROP TABLE IF EXISTS `order_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_item` (
  `item_id` bigint NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_num` bigint NOT NULL COMMENT '所属订单编号',
  `dish_number` bigint NOT NULL COMMENT '菜品编号',
  `quantity` int DEFAULT '1' COMMENT '数量',
  `price_at_time` decimal(10,2) NOT NULL COMMENT '下单时价格',
  PRIMARY KEY (`item_id`),
  KEY `dish_number` (`dish_number`),
  KEY `idx_order` (`order_num`),
  CONSTRAINT `order_item_ibfk_1` FOREIGN KEY (`order_num`) REFERENCES `orders` (`order_num`),
  CONSTRAINT `order_item_ibfk_2` FOREIGN KEY (`dish_number`) REFERENCES `dish` (`dish_number`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_item`
--

LOCK TABLES `order_item` WRITE;
/*!40000 ALTER TABLE `order_item` DISABLE KEYS */;
INSERT INTO `order_item` VALUES (1,1,3,2,58.00),(2,1,20,1,8.00),(3,2,6,1,68.00),(4,4,3,1,58.00),(5,5,3,1,58.00),(6,6,11,1,26.00),(7,6,7,1,36.00),(8,7,9,1,78.00),(9,7,11,1,26.00),(10,7,7,1,36.00),(11,8,20,2,8.00),(12,9,20,2,8.00),(13,10,20,2,8.00),(14,11,20,2,8.00),(15,12,20,2,8.00),(16,13,6,1,68.00),(17,13,12,1,22.00),(18,13,20,2,8.00),(19,14,11,1,26.00),(23,16,20,1,8.00),(24,17,20,1,8.00),(25,17,12,1,22.00),(26,17,46,1,22.00),(27,18,70,1,28.00);
/*!40000 ALTER TABLE `order_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_num` bigint NOT NULL AUTO_INCREMENT COMMENT '订单编号',
  `order_user_id` bigint DEFAULT NULL COMMENT '下单用户ID',
  `order_total_money` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `order_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `order_status` enum('pending','paid','cancelled') DEFAULT 'pending' COMMENT '订单状态',
  `draft_id` varchar(36) DEFAULT NULL COMMENT '记录订单来自哪份草稿',
  `draft_owner_key` varchar(128) DEFAULT NULL COMMENT '记录归属，重复确认时检查是否属于当前用户，游客也适用稿',
  PRIMARY KEY (`order_num`),
  UNIQUE KEY `draft_id` (`draft_id`),
  KEY `idx_user` (`order_user_id`),
  KEY `idx_status` (`order_status`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`order_user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,1,124.00,'2026-09-22 23:22:58','paid',NULL,NULL),(2,1,68.00,'2026-09-22 23:23:44','pending',NULL,NULL),(4,1,58.00,'2026-09-23 17:08:35','pending',NULL,NULL),(5,3,58.00,'2026-09-23 18:08:04','pending',NULL,NULL),(6,NULL,62.00,'2026-10-07 00:16:31','pending',NULL,NULL),(7,3,140.00,'2026-10-07 00:21:05','pending',NULL,NULL),(8,NULL,16.00,'2026-10-13 19:37:29','pending','51d35393-2a38-44c9-9746-7d68b2b805af','guestF339A1541F2D929FEDA6E031CF3D3111'),(9,NULL,16.00,'2026-10-13 19:52:35','pending','08e91a5a-64ba-4eaa-b695-94baca489a03','guestF339A1541F2D929FEDA6E031CF3D3111'),(10,NULL,16.00,'2026-10-13 19:58:47','pending','92c4932f-45bf-48dd-a09b-333616ef0756','guest9954491ED96BDF03FF0D5C7BA359C4F7'),(11,NULL,16.00,'2026-10-13 20:24:54','pending','3e6a41c3-12d5-43bb-9617-5f3c86ea013c','guest393AEFEEBA2A195397063A05FBDCCD1C'),(12,1,16.00,'2026-10-13 20:42:18','pending','2d448f9f-1378-47aa-887d-fdb5c45da220','user:1'),(13,NULL,106.00,'2026-10-21 17:24:35','pending','189e798b-2b7b-42ba-8205-05547f23e72c','guest59A5AFB18CD6250AD253C0C4CBD625CF'),(14,3,26.00,'2026-10-21 17:28:46','pending','fb0132a5-3fa1-4d2c-b1be-cb2c066d5b96','user:3'),(16,NULL,8.00,'2026-10-27 08:49:48','pending','7ede6084-bd43-46ba-9f51-d05b080369ef','guest1D61B56A42B73650EABEAF533C54FAF1'),(17,3,52.00,'2026-11-02 09:26:55','pending','2706e231-01c0-44bf-923e-a24ed48a33b1','user:3'),(18,3,28.00,'2026-11-03 16:40:40','pending','25b5ae03-4829-465a-81cd-119bd2d65a80','user:3');
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `recommendation_log`
--

DROP TABLE IF EXISTS `recommendation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `recommendation_log` (
  `log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `recommended_dishes` json DEFAULT NULL COMMENT '推荐菜品编号列表 [1,3,5]',
  `ordered_dishes` json DEFAULT NULL COMMENT '实际点餐菜品编号 [1,3]',
  `weather` varchar(20) DEFAULT NULL COMMENT '天气情况',
  `context` json DEFAULT NULL COMMENT '上下文特征：{"last_meal":"lunch","season":"summer"}',
  `recommendation_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
  `feedback_score` tinyint DEFAULT NULL COMMENT '反馈分数 1-5',
  PRIMARY KEY (`log_id`),
  KEY `idx_user_time` (`user_id`,`recommendation_time`),
  CONSTRAINT `recommendation_log_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI推荐日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `recommendation_log`
--

LOCK TABLES `recommendation_log` WRITE;
/*!40000 ALTER TABLE `recommendation_log` DISABLE KEYS */;
INSERT INTO `recommendation_log` VALUES (1,1,'[1]',NULL,NULL,NULL,'2026-09-16 13:43:09',NULL),(2,1,'[3, 4, 6, 11, 18]',NULL,NULL,NULL,'2026-09-16 13:50:41',NULL),(3,1,'[6, 12, 15, 19, 20]',NULL,NULL,NULL,'2026-09-16 13:54:35',NULL),(4,1,'[7, 18, 19, 20, 21]',NULL,NULL,NULL,'2026-09-16 13:55:48',NULL),(5,1,'[7, 15, 18, 19, 20]',NULL,NULL,NULL,'2026-09-16 13:57:03',NULL),(6,1,'[4, 5, 11, 15, 16]',NULL,NULL,NULL,'2026-09-16 13:59:17',NULL),(7,1,'[1, 5, 7, 8, 20]',NULL,NULL,NULL,'2026-09-16 14:00:37',NULL),(8,1,'[12, 13, 15, 16, 17]',NULL,NULL,NULL,'2026-09-16 14:01:08',NULL),(9,1,'[5, 9, 11, 16, 17]',NULL,NULL,NULL,'2026-09-16 14:01:34',NULL),(10,1,'[3, 4, 6, 11, 18]',NULL,NULL,NULL,'2026-09-16 14:08:55',NULL),(11,1,'[7, 15, 18, 19, 20]',NULL,NULL,NULL,'2026-09-16 14:09:54',NULL),(12,1,'[5, 9, 11, 16, 17]',NULL,NULL,NULL,'2026-09-16 14:13:31',NULL),(13,1,'[5, 9, 11, 16, 17]',NULL,NULL,NULL,'2026-09-16 14:14:07',NULL),(14,1,'[3, 4, 6, 11, 18]',NULL,NULL,NULL,'2026-09-16 14:21:04',NULL),(15,1,'[18, 3, 6, 4, 11]',NULL,NULL,NULL,'2026-09-16 14:50:46',NULL),(16,1,'[18, 3, 6, 4, 11]',NULL,NULL,NULL,'2026-09-16 18:43:37',NULL),(17,1,'[18, 3, 6, 4, 11]',NULL,NULL,NULL,'2026-09-16 18:45:13',NULL),(18,1,'[3, 11, 7, 9, 1]',NULL,NULL,NULL,'2026-09-16 20:43:35',NULL),(19,1,'[3, 11, 7, 9, 1]',NULL,NULL,NULL,'2026-09-17 19:07:33',NULL),(20,1,'[3, 11, 7, 9, 1]',NULL,NULL,NULL,'2026-09-19 14:22:09',NULL),(21,1,'[12, 4, 6, 19, 10]',NULL,NULL,NULL,'2026-09-19 14:23:50',NULL),(22,1,'[12, 17, 13, 15, 14]',NULL,NULL,NULL,'2026-09-19 14:24:21',NULL),(23,1,'[20, 19, 16, 7, 9]',NULL,NULL,NULL,'2026-09-19 14:24:52',NULL),(24,1,'[5, 11, 16, 17, 9]',NULL,NULL,NULL,'2026-09-19 14:25:14',NULL),(25,1,'[11, 15, 16, 7, 8]',NULL,NULL,NULL,'2026-09-19 14:25:57',NULL),(26,1,'[12, 4, 6, 19, 10]',NULL,NULL,NULL,'2026-09-19 14:27:08',NULL),(27,1,'[5, 11, 16, 17, 9]',NULL,NULL,NULL,'2026-09-19 14:27:55',NULL),(28,1,'[11, 7, 3, 9, 1]',NULL,NULL,NULL,'2026-09-19 17:01:07',NULL),(29,1,'[6, 12, 20, 19, 4]',NULL,NULL,NULL,'2026-09-19 17:08:37',NULL),(30,2,'[6, 12, 15, 20, 19]',NULL,NULL,NULL,'2026-09-19 17:09:17',NULL),(31,2,'[6, 12, 15, 20, 19]',NULL,NULL,NULL,'2026-09-19 17:19:48',NULL),(32,1,'[12, 20, 19, 4, 6]',NULL,NULL,NULL,'2026-09-19 17:20:28',NULL),(33,1,'[12, 15, 20, 19, 6]',NULL,NULL,NULL,'2026-09-19 19:09:31',NULL),(34,2,'[6, 12, 20, 19, 4]',NULL,NULL,NULL,'2026-09-19 19:10:09',NULL),(35,1,'[6, 12, 20, 19, 4]',NULL,NULL,NULL,'2026-09-21 22:19:03',NULL),(36,1,'[12, 20, 19, 4, 6]',NULL,NULL,NULL,'2026-09-21 22:22:21',NULL),(37,1,'[11, 7, 3, 1, 9]','[3]',NULL,NULL,'2026-09-23 17:06:55',NULL),(38,3,'[3, 11, 7, 9, 1]','[3]',NULL,NULL,'2026-09-23 18:07:48',NULL),(39,3,'[17, 8, 16, 4, 13]',NULL,NULL,NULL,'2026-09-23 18:12:16',NULL),(40,3,'[17, 8, 16, 4, 13]',NULL,NULL,NULL,'2026-09-23 18:26:02',NULL),(41,3,'[9, 11, 7, 3, 1]',NULL,NULL,NULL,'2026-09-23 18:27:35',NULL),(42,3,'[16, 20, 15, 11, 7]',NULL,NULL,NULL,'2026-09-23 22:41:28',NULL),(43,3,'[5, 11, 16, 17, 9]',NULL,NULL,NULL,'2026-09-23 22:41:52',NULL),(44,3,'[3, 9, 11, 7, 1]',NULL,NULL,NULL,'2026-09-23 22:42:40',NULL),(45,3,'[3, 9, 11, 7, 1]',NULL,NULL,NULL,'2026-09-23 22:50:05',NULL),(46,3,'[9, 11, 7, 3, 1]',NULL,NULL,NULL,'2026-09-23 23:16:50',NULL),(47,3,'[9, 11, 7, 3, 1]',NULL,NULL,NULL,'2026-09-23 23:16:59',NULL),(48,3,'[9, 16, 11, 7, 15]',NULL,NULL,NULL,'2026-09-23 23:17:30',NULL),(49,3,'[9, 11, 7, 3, 1]',NULL,NULL,NULL,'2026-09-23 23:26:26',NULL),(50,1,'[12, 18, 19, 20]',NULL,NULL,NULL,'2026-10-06 17:14:05',NULL),(51,1,'[12, 15, 20, 19, 6]',NULL,NULL,NULL,'2026-10-06 18:17:09',NULL),(52,1,'[12, 20, 19, 4, 6]',NULL,NULL,NULL,'2026-10-06 18:26:58',NULL),(53,1,'[12, 15, 20, 19, 6]',NULL,NULL,NULL,'2026-10-06 18:28:31',NULL),(54,1,'[12, 15, 20, 19, 6]','[20]','晴，11°C',NULL,'2026-10-06 18:36:12',NULL),(55,3,'[9, 11, 7, 3, 1]','[9, 11, 7]','晴，6°C',NULL,'2026-10-07 00:20:44',NULL),(56,3,'[9, 11, 7, 3, 1]','[20, 12, 46]','晴，8°C',NULL,'2026-10-07 01:10:33',NULL),(60,3,'[12, 46, 57, 39, 6]',NULL,'晴，4°C',NULL,'2026-11-03 16:17:01',NULL),(61,3,'[12, 37, 46, 39, 6]',NULL,'晴，4°C',NULL,'2026-11-03 16:21:10',NULL),(62,3,'[70, 79, 71, 69, 76]','[70]','晴，4°C',NULL,'2026-11-03 16:38:57',NULL),(63,3,'[46, 20, 57, 39, 6]',NULL,'多云，1°C',NULL,'2026-11-10 23:17:07',NULL),(64,3,'[46, 20, 57, 39, 6]',NULL,'多云，1°C',NULL,'2026-11-10 23:19:00',NULL),(65,3,'[11, 45, 42, 24, 72]',NULL,'多云，1°C',NULL,'2026-11-10 23:30:38',NULL),(66,3,'[46, 20, 57, 39, 6]',NULL,'多云，1°C',NULL,'2026-11-10 23:31:19',NULL),(67,3,'[11, 7, 45, 24, 72, 20]',NULL,'雾，6°C',NULL,'2026-11-12 23:18:05',NULL),(68,3,'[11, 12, 7, 4, 70, 20]',NULL,'雾，6°C',NULL,'2026-11-12 23:18:32',NULL);
/*!40000 ALTER TABLE `recommendation_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `phone_number` varchar(11) NOT NULL COMMENT '手机号',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `user_created_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `user_status` enum('active','disabled') DEFAULT 'active' COMMENT '用户状态',
  `balance` decimal(10,2) DEFAULT '0.00' COMMENT '余额（预留）',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `phone_number` (`phone_number`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'13800002222','食客2222','2026-09-11 18:24:36','active',0.00),(2,'13800001111','食客1111','2026-09-11 18:26:14','active',0.00),(3,'13800000001','wuyou','2026-09-23 18:06:30','active',0.00),(4,'13800000001','测试用户','2026-10-06 19:51:54','active',0.00),(8,'13800000002','wuyou','2026-11-10 18:12:17','active',0.00);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_dish_feedback`
--

DROP TABLE IF EXISTS `user_dish_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_dish_feedback` (
  `feedback_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dish_number` bigint NOT NULL,
  `feedback_score` tinyint NOT NULL,
  `feedback_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`feedback_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_user_dish` (`user_id`,`dish_number`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_dish_feedback`
--

LOCK TABLES `user_dish_feedback` WRITE;
/*!40000 ALTER TABLE `user_dish_feedback` DISABLE KEYS */;
INSERT INTO `user_dish_feedback` VALUES (1,1,7,4,'2026-09-19 17:07:28'),(2,1,7,5,'2026-09-19 17:07:28'),(3,1,7,4,'2026-09-19 17:07:28'),(4,1,9,2,'2026-09-19 17:07:28'),(5,1,11,5,'2026-09-19 17:07:28'),(6,1,11,5,'2026-09-19 17:07:28'),(7,1,11,5,'2026-09-19 17:07:28'),(8,1,11,4,'2026-09-19 17:07:28'),(9,1,11,5,'2026-09-19 17:07:28'),(10,1,3,3,'2026-09-19 17:07:28'),(11,1,3,3,'2026-09-19 17:07:28'),(12,1,6,2,'2026-09-19 17:07:28'),(13,2,9,1,'2026-09-19 17:07:28'),(14,2,10,4,'2026-09-19 17:07:28'),(15,2,10,5,'2026-09-19 17:07:28'),(16,2,10,4,'2026-09-19 17:07:28'),(17,2,10,5,'2026-09-19 17:07:28'),(18,2,11,2,'2026-09-19 17:07:28'),(19,2,12,4,'2026-09-19 17:07:28'),(20,2,12,4,'2026-09-19 17:07:28'),(21,2,12,3,'2026-09-19 17:07:28'),(22,2,3,1,'2026-09-19 17:07:28'),(23,2,4,3,'2026-09-19 17:07:28'),(24,2,4,3,'2026-09-19 17:07:28'),(25,2,6,5,'2026-09-19 17:07:28'),(26,2,6,5,'2026-09-19 17:07:28'),(27,2,6,5,'2026-09-19 17:07:28'),(28,2,6,5,'2026-09-19 17:07:28'),(29,2,6,4,'2026-09-19 17:07:28'),(30,3,9,4,'2026-10-07 00:21:12'),(31,3,11,4,'2026-10-07 00:21:13'),(32,3,7,3,'2026-10-07 00:21:16'),(33,3,9,4,'2026-10-07 00:34:57'),(34,3,11,4,'2026-10-07 00:34:57'),(37,3,70,5,'2026-11-03 16:40:55'),(38,3,20,4,'2026-11-03 16:41:03'),(39,3,12,2,'2026-11-03 16:41:03'),(40,3,46,5,'2026-11-03 16:41:03');
/*!40000 ALTER TABLE `user_dish_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_preference`
--

DROP TABLE IF EXISTS `user_preference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_preference` (
  `user_id` bigint NOT NULL COMMENT '用户ID，与用户表一对一，不自增',
  `spicy_level` enum('none','mild','medium','high') DEFAULT NULL COMMENT 'NULL未设置，none不辣，mild微辣，medium中辣，high重辣',
  `preference_text` varchar(200) DEFAULT NULL COMMENT '用户确认的补充口味说明',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次保存时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近修改时间',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户长期口味画像';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_preference`
--

LOCK TABLES `user_preference` WRITE;
/*!40000 ALTER TABLE `user_preference` DISABLE KEYS */;
INSERT INTO `user_preference` VALUES (3,'medium','偏好中辣；口味偏好下饭；不吃香菜。','2026-10-21 17:26:57','2026-10-21 17:26:57');
/*!40000 ALTER TABLE `user_preference` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'smart_ordering'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-16  8:41:37
