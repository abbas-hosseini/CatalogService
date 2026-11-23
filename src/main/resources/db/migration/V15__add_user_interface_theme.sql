ALTER TABLE app_users ADD COLUMN ui_theme VARCHAR(20) NOT NULL DEFAULT 'classic';
ALTER TABLE app_users ADD CONSTRAINT ck_app_user_theme CHECK (ui_theme IN ('classic', 'ocean', 'violet', 'dark'));
