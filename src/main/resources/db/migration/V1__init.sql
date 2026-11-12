-- 家庭点餐 v1 初始结构
-- 约定：所有家庭内业务表都带 family_id，用于租户隔离。

CREATE TABLE app_user (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  nickname      VARCHAR(64)  NOT NULL,
  avatar_url    VARCHAR(512) NULL,
  wx_openid     VARCHAR(64)  NULL,
  wx_unionid    VARCHAR(64)  NULL,
  username      VARCHAR(64)  NULL,
  password_hash VARCHAR(100) NULL,
  system_admin  BIT(1)       NOT NULL DEFAULT b'0',
  created_at    DATETIME(6)  NOT NULL,
  updated_at    DATETIME(6)  NOT NULL,
  CONSTRAINT uk_user_openid UNIQUE (wx_openid),
  CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE family (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  name               VARCHAR(64) NOT NULL,
  invite_code        VARCHAR(16) NOT NULL,
  -- 早餐截止时间按「前一天」计算，午餐、晚餐按当天计算
  breakfast_deadline TIME        NOT NULL DEFAULT '21:00:00',
  lunch_deadline     TIME        NOT NULL DEFAULT '10:00:00',
  dinner_deadline    TIME        NOT NULL DEFAULT '16:00:00',
  created_by         BIGINT      NOT NULL,
  created_at         DATETIME(6) NOT NULL,
  updated_at         DATETIME(6) NOT NULL,
  CONSTRAINT uk_family_invite UNIQUE (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE family_member (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id     BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  role          VARCHAR(16)  NOT NULL,
  display_name  VARCHAR(64)  NOT NULL,
  dietary_notes VARCHAR(255) NULL,
  allergies     VARCHAR(255) NULL,
  joined_at     DATETIME(6)  NOT NULL,
  CONSTRAINT uk_member_family_user UNIQUE (family_id, user_id),
  CONSTRAINT fk_member_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_member_user ON family_member (user_id);

CREATE TABLE dish (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id        BIGINT       NOT NULL,
  name             VARCHAR(64)  NOT NULL,
  category         VARCHAR(32)  NOT NULL,
  difficulty       VARCHAR(16)  NOT NULL,
  duration_minutes INT          NOT NULL,
  default_servings INT          NOT NULL,
  cover_url        VARCHAR(512) NULL,
  description      VARCHAR(500) NULL,
  tips             VARCHAR(1000) NULL,
  active           BIT(1)       NOT NULL DEFAULT b'1',
  created_by       BIGINT       NULL,
  created_at       DATETIME(6)  NOT NULL,
  updated_at       DATETIME(6)  NOT NULL,
  CONSTRAINT fk_dish_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_dish_family ON dish (family_id, active);

CREATE TABLE dish_step (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  dish_id          BIGINT        NOT NULL,
  step_no          INT           NOT NULL,
  content          VARCHAR(1000) NOT NULL,
  duration_minutes INT           NULL,
  CONSTRAINT fk_step_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE dish_ingredient (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dish_id    BIGINT        NOT NULL,
  kind       VARCHAR(16)   NOT NULL, -- MAIN 主料 / SEASONING 配料调味
  name       VARCHAR(64)   NOT NULL,
  amount     DECIMAL(10,2) NULL,     -- 为空表示「适量」
  unit       VARCHAR(16)   NULL,
  staple     BIT(1)        NOT NULL DEFAULT b'0', -- 家里常备，默认不进采购清单
  note       VARCHAR(255)  NULL,
  sort_order INT           NOT NULL DEFAULT 0,
  CONSTRAINT fk_ing_dish FOREIGN KEY (dish_id) REFERENCES dish (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE menu (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id         BIGINT       NOT NULL,
  meal_date         DATE         NOT NULL,
  meal_type         VARCHAR(16)  NOT NULL,
  status            VARCHAR(16)  NOT NULL, -- OPEN / LOCKED / CONFIRMED / READY
  deadline_override DATETIME(6)  NULL,
  note              VARCHAR(500) NULL,
  confirmed_by      BIGINT       NULL,
  confirmed_at      DATETIME(6)  NULL,
  ready_at          DATETIME(6)  NULL,
  created_at        DATETIME(6)  NOT NULL,
  updated_at        DATETIME(6)  NOT NULL,
  version           BIGINT       NOT NULL DEFAULT 0,
  CONSTRAINT uk_menu_slot UNIQUE (family_id, meal_date, meal_type),
  CONSTRAINT fk_menu_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_menu_status_date ON menu (status, meal_date);

CREATE TABLE menu_item (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  menu_id                BIGINT       NOT NULL,
  dish_id                BIGINT       NULL,
  custom_name            VARCHAR(64)  NULL,
  servings               INT          NOT NULL,
  source                 VARCHAR(16)  NOT NULL, -- WISH / COOK_ADDED / SUBSTITUTE
  substitute_for_dish_id BIGINT       NULL,
  note                   VARCHAR(255) NULL,
  sort_order             INT          NOT NULL DEFAULT 0,
  CONSTRAINT fk_mi_menu FOREIGN KEY (menu_id) REFERENCES menu (id) ON DELETE CASCADE,
  CONSTRAINT fk_mi_dish FOREIGN KEY (dish_id) REFERENCES dish (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE meal_wish (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id        BIGINT       NOT NULL,
  member_id        BIGINT       NOT NULL,
  meal_date        DATE         NOT NULL,
  meal_type        VARCHAR(16)  NOT NULL,
  dish_id          BIGINT       NULL,
  free_text        VARCHAR(64)  NULL,
  intensity        VARCHAR(16)  NOT NULL, -- WANT / OK / AVOID
  servings         INT          NOT NULL DEFAULT 1,
  not_eating_home  BIT(1)       NOT NULL DEFAULT b'0',
  note             VARCHAR(255) NULL,
  created_at       DATETIME(6)  NOT NULL,
  updated_at       DATETIME(6)  NOT NULL,
  CONSTRAINT fk_wish_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_wish_member FOREIGN KEY (member_id) REFERENCES family_member (id) ON DELETE CASCADE,
  CONSTRAINT fk_wish_dish FOREIGN KEY (dish_id) REFERENCES dish (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_wish_slot ON meal_wish (family_id, meal_date, meal_type);

CREATE TABLE shopping_list (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id    BIGINT      NOT NULL,
  menu_id      BIGINT      NOT NULL,
  generated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_shopping_menu UNIQUE (menu_id),
  CONSTRAINT fk_sl_family FOREIGN KEY (family_id) REFERENCES family (id),
  CONSTRAINT fk_sl_menu FOREIGN KEY (menu_id) REFERENCES menu (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE shopping_item (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  list_id        BIGINT        NOT NULL,
  name           VARCHAR(64)   NOT NULL,
  amount         DECIMAL(10,2) NULL,
  unit           VARCHAR(16)   NULL,
  kind           VARCHAR(16)   NOT NULL,
  staple         BIT(1)        NOT NULL DEFAULT b'0',
  to_buy         BIT(1)        NOT NULL DEFAULT b'1',
  purchased      BIT(1)        NOT NULL DEFAULT b'0',
  source_dishes  VARCHAR(255)  NULL,
  sort_order     INT           NOT NULL DEFAULT 0,
  CONSTRAINT fk_si_list FOREIGN KEY (list_id) REFERENCES shopping_list (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notification (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  family_id     BIGINT       NOT NULL,
  menu_id       BIGINT       NULL,
  type          VARCHAR(32)  NOT NULL, -- MENU_CONFIRMED / DINNER_READY
  title         VARCHAR(64)  NOT NULL,
  content       VARCHAR(255) NOT NULL,
  wechat_status VARCHAR(16)  NOT NULL, -- SKIPPED / SENT / FAILED
  created_by    BIGINT       NULL,
  created_at    DATETIME(6)  NOT NULL,
  CONSTRAINT fk_notify_family FOREIGN KEY (family_id) REFERENCES family (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_notify_family ON notification (family_id, created_at);

-- ---------------------------------------------------------------
-- 临时聚餐（组队）预留表：v1 只建表，不做完整界面。
-- 数据边界：不引用 family_member，也不直接引用家庭菜谱 dish，
-- 菜名以快照形式保存；临时队员只在本场聚餐内有效。
-- host_family_id 只用于在家庭日历上占位，不授予任何家庭权限。
-- ---------------------------------------------------------------
CREATE TABLE gathering (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  host_user_id   BIGINT       NOT NULL,
  host_family_id BIGINT       NULL,
  title          VARCHAR(64)  NOT NULL,
  meal_date      DATE         NOT NULL,
  meal_type      VARCHAR(16)  NOT NULL,
  location       VARCHAR(128) NULL,
  mode           VARCHAR(16)  NOT NULL, -- HOST_COOK 主人掌厨 / VOTE 众筹菜单 / POTLUCK 各带一道
  status         VARCHAR(16)  NOT NULL, -- OPEN / CONFIRMED / CLOSED
  invite_token   VARCHAR(32)  NOT NULL,
  deadline_at    DATETIME(6)  NULL,
  closed_at      DATETIME(6)  NULL,
  created_at     DATETIME(6)  NOT NULL,
  CONSTRAINT uk_gathering_token UNIQUE (invite_token),
  CONSTRAINT fk_gathering_host FOREIGN KEY (host_user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gathering_participant (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  gathering_id  BIGINT       NOT NULL,
  user_id       BIGINT       NULL,
  guest_name    VARCHAR(64)  NOT NULL,
  headcount     INT          NOT NULL DEFAULT 1,
  dietary_notes VARCHAR(255) NULL,
  rsvp          VARCHAR(16)  NOT NULL, -- YES / NO / MAYBE
  joined_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_gp_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE gathering_wish (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  gathering_id   BIGINT       NOT NULL,
  participant_id BIGINT       NOT NULL,
  dish_name      VARCHAR(64)  NOT NULL,
  bring_by_self  BIT(1)       NOT NULL DEFAULT b'0',
  votes          INT          NOT NULL DEFAULT 0,
  note           VARCHAR(255) NULL,
  created_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_gw_gathering FOREIGN KEY (gathering_id) REFERENCES gathering (id) ON DELETE CASCADE,
  CONSTRAINT fk_gw_participant FOREIGN KEY (participant_id) REFERENCES gathering_participant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
