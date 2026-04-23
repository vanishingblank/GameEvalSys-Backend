-- 为项目增加恶意打分判定规则配置
ALTER TABLE `project`
    ADD COLUMN IF NOT EXISTS `malicious_rule_type` varchar(20) NOT NULL DEFAULT 'AUTO' COMMENT '恶意打分判定规则：AUTO/THRESHOLD' AFTER `standard_id`,
    ADD COLUMN IF NOT EXISTS `malicious_score_lower` decimal(10,2) DEFAULT NULL COMMENT '恶意阈值下限（THRESHOLD模式）' AFTER `malicious_rule_type`,
    ADD COLUMN IF NOT EXISTS `malicious_score_upper` decimal(10,2) DEFAULT NULL COMMENT '恶意阈值上限（THRESHOLD模式）' AFTER `malicious_score_lower`;

