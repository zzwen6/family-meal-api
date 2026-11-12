-- 家庭点餐 v2：完整临时聚餐 + 聚餐专属站内通知
-- 只做增量变更，不改 V1（V1 可能已在用户库上执行过）。
-- 数据边界不变：聚餐表都不引用 family_member / dish；菜名一律是快照。

-- 聚餐本身：备注、确认 / 开饭时间、确认时的出席人数（人数变了好提示主人）、从哪一场复制来
ALTER TABLE gathering
  ADD COLUMN note                VARCHAR(500) NULL AFTER location,
  ADD COLUMN confirmed_at        DATETIME(6)  NULL AFTER deadline_at,
  ADD COLUMN confirmed_headcount INT          NULL AFTER confirmed_at,
  ADD COLUMN ready_at            DATETIME(6)  NULL AFTER confirmed_headcount,
  ADD COLUMN source_gathering_id BIGINT       NULL AFTER ready_at;
CREATE INDEX idx_gathering_family_date ON gathering (host_family_id, meal_date);

-- 同一个登录用户在一场聚餐里只有一条参与记录（user_id 为空的是主人代填的称呼，不受限制）
ALTER TABLE gathering_participant
  ADD CONSTRAINT uk_gp_gathering_user UNIQUE (gathering_id, user_id);
CREATE INDEX idx_gp_user ON gathering_participant (user_id);

CREATE INDEX idx_gw_gathering ON gathering_wish (gathering_id);

-- 众筹投票：一人一菜一票
CREATE TABLE gathering_vote (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  gathering_id   BIGINT      NOT NULL,
  wish_id        BIGINT      NOT NULL,
  participant_id BIGINT      NOT NULL,
  created_at     DATETIME(6) NOT NULL,
  CONSTRAINT uk_gv_wish_participant UNIQUE (wish_id, participant_id),
  CONSTRAINT fk_gv_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id) ON DELETE CASCADE,
  CONSTRAINT fk_gv_wish FOREIGN KEY (wish_id) REFERENCES gathering_wish (id) ON DELETE CASCADE,
  CONSTRAINT fk_gv_participant FOREIGN KEY (participant_id) REFERENCES gathering_participant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 发起人确认的菜单。servings 为空表示「按出席总人数」，人数变了自动跟着变
CREATE TABLE gathering_menu_item (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  gathering_id         BIGINT       NOT NULL,
  dish_name            VARCHAR(64)  NOT NULL,
  servings             INT          NULL,
  wish_id              BIGINT       NULL,
  bring_participant_id BIGINT       NULL, -- 各带一道：谁带
  note                 VARCHAR(255) NULL,
  sort_order           INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_gmi_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id) ON DELETE CASCADE,
  CONSTRAINT fk_gmi_wish FOREIGN KEY (wish_id) REFERENCES gathering_wish (id) ON DELETE SET NULL,
  CONSTRAINT fk_gmi_participant FOREIGN KEY (bring_participant_id) REFERENCES gathering_participant (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 聚餐采购清单：菜名是快照，不能套用家庭菜谱用料，由发起人手填。
-- base_servings 不为空时，amount 表示「base_servings 人份的用量」，展示时按出席总人数换算。
CREATE TABLE gathering_shopping_item (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  gathering_id  BIGINT        NOT NULL,
  name          VARCHAR(64)   NOT NULL,
  amount        DECIMAL(10,2) NULL,
  unit          VARCHAR(16)   NULL,
  base_servings INT           NULL,
  for_dish      VARCHAR(64)   NULL,
  note          VARCHAR(255)  NULL,
  purchased     BIT(1)        NOT NULL DEFAULT b'0',
  sort_order    INT           NOT NULL DEFAULT 0,
  created_at    DATETIME(6)   NOT NULL,
  CONSTRAINT fk_gsi_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 站内通知扩展到聚餐：家庭通知 family_id 有值，聚餐通知 gathering_id 有值，二者互斥。
-- 家庭通知接口只按 family_id 查，聚餐通知不会混进家庭。
-- （MySQL 不允许 CHECK 引用带 ON DELETE 动作的外键列，所以这里的外键不级联；聚餐不做物理删除。）
ALTER TABLE notification
  MODIFY COLUMN family_id BIGINT NULL,
  ADD COLUMN gathering_id BIGINT NULL AFTER family_id,
  ADD CONSTRAINT fk_notify_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id),
  ADD CONSTRAINT ck_notify_scope CHECK ((family_id IS NULL) <> (gathering_id IS NULL));
CREATE INDEX idx_notify_gathering ON notification (gathering_id, created_at);
