output "ecr_repository_url" {
  description = "ECR 리포지토리 URL"
  value       = module.ecr.repository_url
}

output "eks_cluster_name" {
  description = "EKS 클러스터 이름"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS 클러스터 엔드포인트"
  value       = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  description = "RDS 엔드포인트"
  value       = module.rds.endpoint
}

output "elasticache_endpoint" {
  description = "ElastiCache 기본 엔드포인트"
  value       = module.elasticache.primary_endpoint
}

output "msk_bootstrap_brokers" {
  description = "MSK 브로커 주소 (PLAINTEXT)"
  value       = module.msk.bootstrap_brokers
}
