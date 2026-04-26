# Keycloak ローカル開発用イメージ。
# realm-export.json をイメージに焼き込み、起動時に自動インポートする。
# DevContainer で compose を回すとバインドマウントのパス解決が
# ホスト側でずれて空ディレクトリになるため、ビルドコンテキスト経由で
# ファイルを取り込む方式にしている。
FROM quay.io/keycloak/keycloak:26.0

COPY realm-export.json /opt/keycloak/data/import/realm-export.json
