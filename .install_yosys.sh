set -e
# Install Yosys (https://github.com/cliffordwolf/yosys)
if [ ! -f $INSTALL_DIR/bin/yosys ]; then
  mkdir -p $INSTALL_DIR
  git clone https://github.com/cliffordwolf/yosys.git
  cd yosys
  git pull
  git checkout master
  make
  make PREFIX=$INSTALL_DIR install
fi
