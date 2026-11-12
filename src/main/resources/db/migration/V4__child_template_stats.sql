-- 儿童监护、周菜单模板与计划、餐后评价。
-- 不改 V1/V2/V3；聚餐数据不进入这些表。

ALTER TABLE family_member
  ADD COLUMN guardian_member_id BIGINT NULL AFTER allergies,
  ADD CONSTRAINT fk_member_guardian
    FOREIGN KEY (guardian_member_id) REFERENCES family_member (id) ON DELETE SET NULL;
CREATE INDEX idx_member_guardian ON family_member (guardian_member_id);

-- 一周菜单模板：周一到周日、各餐次的菜（家庭菜谱或手写菜名）。
CREATE TABLE week_template (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id  BIGINT       NOT NULL,
  name       VARCHAR(64)  NOT NULL,
  note       VARCHAR(255) NULL,
  created_by BIGINT       NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  CONSTRAINT fk_wt_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_wt_family ON week_template (family_id);

CREATE TABLE week_template_item (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT       NOT NULL,
  day_of_week INT          NOT NULL,
  meal_type   VARCHAR(16)  NOT NULL,
  dish_id     BIGINT       NULL,
  custom_name VARCHAR(64)  NULL,
  servings    INT          NOT NULL DEFAULT 1,
  sort_order  INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_wti_template FOREIGN KEY (template_id) REFERENCES week_template (id) ON DELETE CASCADE,
  CONSTRAINT fk_wti_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_wti_template ON week_template_item (template_id);

-- 应用到某周后的预填计划。不是已确认菜单；已确认 / 已开饭的餐次不会被覆盖。
CREATE TABLE meal_plan_item (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id          BIGINT       NOT NULL,
  meal_date          DATE         NOT NULL,
  meal_type          VARCHAR(16)  NOT NULL,
  dish_id            BIGINT       NULL,
  custom_name        VARCHAR(64)  NULL,
  servings           INT          NOT NULL DEFAULT 1,
  sort_order         INT          NOT NULL DEFAULT 0,
  source_template_id BIGINT       NULL,
  created_at         DATETIME(6)  NOT NULL,
  updated_at         DATETIME(6)  NOT NULL,
  CONSTRAINT fk_mpi_family   FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_mpi_dish     FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE SET NULL,
  CONSTRAINT fk_mpi_template FOREIGN KEY (source_template_id) REFERENCES week_template (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_mpi_slot ON meal_plan_item (family_id, meal_date, meal_type);

-- 餐后评价：仅已开饭（READY）的家庭日常菜单，每人每餐一条，可改。不含聚餐。
CREATE TABLE meal_rating (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id  BIGINT       NOT NULL,
  menu_id    BIGINT       NOT NULL,
  member_id  BIGINT       NOT NULL,
  score      INT          NOT NULL,
  comment    VARCHAR(255) NULL,
  created_at DATETIME(6)  NOT NULL,
  updated_at DATETIME(6)  NOT NULL,
  CONSTRAINT uk_rating_menu_member UNIQUE (menu_id, member_id),
  CONSTRAINT fk_rating_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_rating_menu   FOREIGN KEY (menu_id)   REFERENCES menu (id) ON DELETE CASCADE,
  CONSTRAINT fk_rating_member FOREIGN KEY (member_id) REFERENCES family_member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_rating_family ON meal_rating (family_id, menu_id);
