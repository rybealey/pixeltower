# Imager

Clone the imager source (from the tutorial repo's `Docker/imager/` folder, or upstream) into
`./docker/imager/app/`. The compose service bind-mounts it and runs `yarn install && yarn start`.

```
git clone https://github.com/duckietm/Complete-Retro-on-Ubuntu tmp
cp -r tmp/Docker/imager ./docker/imager/app
rm -rf tmp
```

Exposes HTTP on internal port 3030; nginx proxies `/imager/` → `imager:3030/`.
