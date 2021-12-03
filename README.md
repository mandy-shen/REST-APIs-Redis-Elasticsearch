### redis
# docker pull redis
# docker run -d -p 6379:6379 redis

### create network first
# docker network create elastic

### es start first
# docker pull docker.elastic.co/elasticsearch/elasticsearch:7.15.2
# docker run -d --name es01-test --net elastic -p 127.0.0.1:9200:9200 -p 127.0.0.1:9300:9300 -e "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:7.15.2

### kibana start last
# docker pull docker.elastic.co/kibana/kibana:7.15.2
# docker run -d --name kib01-test --net elastic -p 127.0.0.1:5601:5601 -e "ELASTICSEARCH_HOSTS=http://es01-test:9200" docker.elastic.co/kibana/kibana:7.15.2

### Kibana console
# http://localhost:5601/app/dev_tools#/console