data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

data "aws_subnet" "selected" {
  for_each = toset(data.aws_subnets.default.ids)
  id       = each.value
}

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "random_id" "suffix" {
  byte_length = 4
}

locals {
  name   = var.project_name
  bucket = "${var.project_name}-${random_id.suffix.hex}"
}

resource "aws_s3_bucket" "documents" {
  bucket        = local.bucket
  force_destroy = false
  tags          = { Project = local.name }

  lifecycle {
    ignore_changes = [
      object_lock_enabled,
    ]
  }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_security_group" "ec2" {
  name_prefix = "${local.name}-ec2-"
  vpc_id      = data.aws_vpc.default.id
  ingress {
    description = "API docente desde IP autorizada"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = [var.allowed_api_cidr]
  }
  ingress {
    description = "SSH para pipeline"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "efs" {
  name_prefix = "${local.name}-efs-"
  vpc_id      = data.aws_vpc.default.id
  ingress {
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_efs_file_system" "shared" {
  creation_token  = local.name
  encrypted       = true
  throughput_mode = "elastic"
  tags            = { Name = "${local.name}-efs" }
}

resource "aws_efs_mount_target" "shared" {
  for_each        = { for s in values(data.aws_subnet.selected) : s.availability_zone => s.id... }
  file_system_id  = aws_efs_file_system.shared.id
  subnet_id       = each.value[0]
  security_groups = [aws_security_group.efs.id]
}

resource "aws_key_pair" "deployer" {
  key_name   = "cdy2204-semana3-key"
  public_key = file("~/.ssh/cdy2204-semana3.pub")
}

resource "aws_instance" "app" {
  ami                         = data.aws_ssm_parameter.al2023.value
  instance_type               = var.instance_type
  subnet_id                   = values(data.aws_subnet.selected)[0].id
  vpc_security_group_ids      = [aws_security_group.ec2.id]
  iam_instance_profile        = "LabInstanceProfile"
  key_name                    = aws_key_pair.deployer.key_name
  associate_public_ip_address = true
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    efs_id = aws_efs_file_system.shared.id
    region = var.region
  })
  user_data_replace_on_change = true
  depends_on                  = [aws_efs_mount_target.shared]
  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }
  tags = { Name = "${local.name}-app", Project = local.name }
}