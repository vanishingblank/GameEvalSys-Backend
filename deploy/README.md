# Deployment Assets

该目录包含面向生产环境的部署模板，不会修改 `src/main/resources` 中现有的开发配置

## 结构

- `docker-single/`: single Linux host deployment with three containers (`backend`, `mariadb`, `redis`)
- `lxc-multi-ct/`: multi-CT deployment plan and files for LXC/Proxmox style environments
- `shared/`: shared database and cache initialization assets

