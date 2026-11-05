#!/bin/sh
set -eu

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/$PROFILE_STORAGE_BUCKET"
mc anonymous set none "local/$PROFILE_STORAGE_BUCKET"

if mc admin user info local "$PROFILE_STORAGE_ACCESS_KEY" >/dev/null 2>&1; then
    :
else
    mc admin user add local "$PROFILE_STORAGE_ACCESS_KEY" "$PROFILE_STORAGE_SECRET_KEY"
fi

policy_file=/tmp/recruitment-document-service.json
cat > "$policy_file" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": ["arn:aws:s3:::$PROFILE_STORAGE_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::$PROFILE_STORAGE_BUCKET/*"]
    }
  ]
}
EOF

if mc admin policy info local recruitment-document-service >/dev/null 2>&1; then
    :
else
    mc admin policy create local recruitment-document-service "$policy_file"
fi

mc admin policy attach local recruitment-document-service \
    --user "$PROFILE_STORAGE_ACCESS_KEY"
