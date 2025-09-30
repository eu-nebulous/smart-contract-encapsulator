mkdir system-genesis-block channel-artifacts
# A: Set the single source of truth for all config files.

export FABRIC_CFG_PATH=/home/ubuntu/smart-contract-encapsulator/nebulous-project-v1/neb-network
export PATH=$PATH:/home/ubuntu/smart-contract-encapsulator/nebulous-fabric-network/bin


sudo chown -R "$(id -u):$(id -g)" /home/ubuntu/smart-contract-encapsulator/nebulous-certificate-authority


# B: Generate the Genesis Block.
echo "===============  Generating Genesis Block  ==============="
configtxgen -profile TwoOrgEtcdRaft -channelID system-channel -outputBlock ./system-genesis-block/genesis.block
echo "✅ Done."
echo


# C: Generate the Channel Transaction.
echo "============= Generating Channel Transaction ============="
configtxgen -profile TwoOrgChannel -outputCreateChannelTx ./channel-artifacts/sla-channel.tx -channelID sla-channel
echo "✅ Done."
echo


# D: Generate the Anchor Peer Updates.
echo "=========== Generating Anchor Peer Transactions ==========="
configtxgen -profile TwoOrgChannel -outputAnchorPeersUpdate ./channel-artifacts/ResourceProviderOrgMSPanchors.tx -channelID sla-channel -asOrg ResourceProviderOrgMSP
configtxgen -profile TwoOrgChannel -outputAnchorPeersUpdate ./channel-artifacts/BrokerOrgMSPanchors.tx -channelID sla-channel -asOrg BrokerOrgMSP
echo "✅ Done."
echo

echo "🎉 All artifacts generated successfully in the 'nebulous-fabric-network' directory! 🎉"