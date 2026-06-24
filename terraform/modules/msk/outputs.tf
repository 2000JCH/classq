output "bootstrap_brokers" {
  value = aws_msk_cluster.this.bootstrap_brokers
}

output "zookeeper_connect_string" {
  value = aws_msk_cluster.this.zookeeper_connect_string
}
