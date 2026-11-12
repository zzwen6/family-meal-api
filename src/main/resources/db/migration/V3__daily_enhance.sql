-- 家庭日常增强：掌厨轮换、吃腻窗口、库存与剩菜。
-- 不改 V1/V2；聚餐数据不进入这些表。

ALTER TABLE family
  ADD COLUMN fatigue_window_days INT NOT NULL DEFAULT 14 AFTER dinner_deadline,
  ADD COLUMN fatigue_threshold   INT NOT NULL DEFAULT 2  AFTER fatigue_window_days;

-- 重新生成采购清单时：若用户曾手工改过「买不买」，保留该勾选；库存对照的自动结果不视为手工改动。
ALTER TABLE shopping_item
  ADD COLUMN note           VARCHAR(255) NULL AFTER source_dishes,
  ADD COLUMN manual_to_buy  BIT(1)       NOT NULL DEFAULT b'0' AFTER note;

-- 轮换设置：一家一行。enabled 表示启用自动安排；meal_types 为逗号分隔的餐次，默认只排晚餐。
CREATE TABLE cook_rotation (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id  BIGINT       NOT NULL,
  enabled    BIT(1)       NOT NULL DEFAULT b'0',
  meal_types VARCHAR(64)  NOT NULL DEFAULT 'DINNER',
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_rotation_family UNIQUE (family_id),
  CONSTRAINT fk_rotation_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 参与轮换的成员及顺序。被排到的人不要求 Role 是 COOK。
CREATE TABLE cook_rotation_member (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  rotation_id BIGINT NOT NULL,
  member_id   BIGINT NOT NULL,
  sort_order  INT    NOT NULL,
  CONSTRAINT uk_rotation_member UNIQUE (rotation_id, member_id),
  CONSTRAINT fk_rm_rotation FOREIGN KEY (rotation_id) REFERENCES cook_rotation (id) ON DELETE CASCADE,
  CONSTRAINT fk_rm_member   FOREIGN KEY (member_id)   REFERENCES family_member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 某日某餐的指定掌厨。source：AUTO 自动生成 / MANUAL 手工指定或对调。
CREATE TABLE cook_assignment (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id  BIGINT       NOT NULL,
  meal_date  DATE         NOT NULL,
  meal_type  VARCHAR(16)  NOT NULL,
  member_id  BIGINT       NOT NULL,
  source     VARCHAR(16)  NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_assignment_slot UNIQUE (family_id, meal_date, meal_type),
  CONSTRAINT fk_ca_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_ca_member FOREIGN KEY (member_id) REFERENCES family_member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_assignment_range ON cook_assignment (family_id, meal_date);

-- 家庭库存（食材/常备）。对照采购时只读数量，不在生成清单时扣减，避免重复扣。
CREATE TABLE inventory_item (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id  BIGINT        NOT NULL,
  name       VARCHAR(64)   NOT NULL,
  amount     DECIMAL(10,2) NULL,
  unit       VARCHAR(16)   NULL,
  staple     BIT(1)        NOT NULL DEFAULT b'0',
  note       VARCHAR(255)  NULL,
  created_at DATETIME(6)   NOT NULL,
  updated_at DATETIME(6)   NOT NULL,
  CONSTRAINT fk_inv_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_inv_family ON inventory_item (family_id);

-- 剩菜：用餐后或随时登记。used=1 后不再参与采购对照。
CREATE TABLE leftover (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id    BIGINT        NOT NULL,
  menu_id      BIGINT        NULL,
  name         VARCHAR(64)   NOT NULL,
  amount       DECIMAL(10,2) NULL,
  unit         VARCHAR(16)   NULL,
  expires_on   DATE          NULL,
  used         BIT(1)        NOT NULL DEFAULT b'0',
  used_at      DATETIME(6)   NULL,
  used_menu_id BIGINT        NULL,
  note         VARCHAR(255)  NULL,
  created_at   DATETIME(6)   NOT NULL,
  updated_at   DATETIME(6)   NOT NULL,
  CONSTRAINT fk_lo_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_lo_menu   FOREIGN KEY (menu_id)   REFERENCES menu (id) ON DELETE SET NULL,
  CONSTRAINT fk_lo_used   FOREIGN KEY (used_menu_id) REFERENCES menu (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_leftover_family ON leftover (family_id, used, expires_on);
