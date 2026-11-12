-- 菜谱步骤图/视频、营养估算覆盖、库存流水与自动增减。
-- 不改 V1–V4；聚餐不进入营养统计和家庭库存。

ALTER TABLE dish
  ADD COLUMN nutrition_override BIT(1)       NOT NULL DEFAULT b'0' AFTER tips,
  ADD COLUMN nutrition_kcal     DECIMAL(10,2) NULL AFTER nutrition_override,
  ADD COLUMN nutrition_protein  DECIMAL(10,2) NULL AFTER nutrition_kcal,
  ADD COLUMN nutrition_fat      DECIMAL(10,2) NULL AFTER nutrition_protein,
  ADD COLUMN nutrition_carb     DECIMAL(10,2) NULL AFTER nutrition_fat;

ALTER TABLE dish_step
  ADD COLUMN image_url              VARCHAR(512) NULL AFTER duration_minutes,
  ADD COLUMN video_url              VARCHAR(512) NULL AFTER image_url,
  ADD COLUMN video_cover_url        VARCHAR(512) NULL AFTER video_url,
  ADD COLUMN video_duration_seconds INT          NULL AFTER video_cover_url;

-- 勾选已买时记入账数量，取消勾选按此撤回，避免按当前购买量重复加减。
ALTER TABLE shopping_item
  ADD COLUMN inventory_offset_amount DECIMAL(10,2) NULL AFTER purchased,
  ADD COLUMN posted_amount           DECIMAL(10,2) NULL AFTER inventory_offset_amount,
  ADD COLUMN posted_unit             VARCHAR(16)   NULL AFTER posted_amount;

-- 开饭时从库存扣「对照时来自库存」的部分，只扣一次。
ALTER TABLE menu
  ADD COLUMN stock_deducted BIT(1) NOT NULL DEFAULT b'0' AFTER ready_at;

-- 用掉剩菜时若按名称匹配到库存食材，记录已扣数量，避免重复扣。
ALTER TABLE leftover
  ADD COLUMN inventory_deducted        BIT(1)        NOT NULL DEFAULT b'0' AFTER used_menu_id,
  ADD COLUMN inventory_deducted_amount DECIMAL(10,2) NULL AFTER inventory_deducted;

-- 上传文件登记：不可猜测的 stored_name，用于访问鉴权和清理旧文件。
CREATE TABLE media_file (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id     BIGINT       NOT NULL,
  stored_name   VARCHAR(80)  NOT NULL,
  original_name VARCHAR(255) NULL,
  content_type  VARCHAR(128) NOT NULL,
  kind          VARCHAR(16)  NOT NULL,
  size_bytes    BIGINT       NOT NULL,
  created_by    BIGINT       NULL,
  created_at    DATETIME(6)  NOT NULL,
  CONSTRAINT uk_media_stored UNIQUE (stored_name),
  CONSTRAINT fk_media_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_media_family ON media_file (family_id);

-- 库存流水：自动入账/扣减与手工改动共用。source_type + source_id 用于幂等。
CREATE TABLE inventory_ledger (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id         BIGINT        NOT NULL,
  inventory_item_id BIGINT        NULL,
  source_type       VARCHAR(32)   NOT NULL,
  source_id         BIGINT        NULL,
  name              VARCHAR(64)   NOT NULL,
  unit              VARCHAR(16)   NULL,
  delta             DECIMAL(10,2) NOT NULL,
  balance_after     DECIMAL(10,2) NULL,
  note              VARCHAR(255)  NULL,
  created_by        BIGINT        NULL,
  created_at        DATETIME(6)   NOT NULL,
  CONSTRAINT fk_il_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_il_item   FOREIGN KEY (inventory_item_id) REFERENCES inventory_item (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_il_family_time ON inventory_ledger (family_id, id);
CREATE INDEX idx_il_source ON inventory_ledger (family_id, source_type, source_id);
