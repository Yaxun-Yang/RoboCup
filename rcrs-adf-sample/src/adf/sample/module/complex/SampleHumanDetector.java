package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanDetector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

//搜索要救的人
public class SampleHumanDetector extends HumanDetector
{
    private Clustering clustering;

    //该救的人里优先级最高的
    private EntityID result;

    public SampleHumanDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData)
    {
        super(ai, wi, si, moduleManager, developData);

        this.result = null;

        switch (scenarioInfo.getMode())
        {
            case PRECOMPUTATION_PHASE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case PRECOMPUTED:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
            case NON_PRECOMPUTE:
                this.clustering = moduleManager.getModule("SampleHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
                break;
        }
        registerModule(this.clustering);
    }

    @Override
    public HumanDetector updateInfo(MessageManager messageManager)
    {
        super.updateInfo(messageManager);
        if (this.getCountUpdateInfo() > 1)
        {
            return this;
        }

        return this;
    }

    @Override
    public HumanDetector calc()
    {
        Human transportHuman = this.agentInfo.someoneOnBoard();
        if (transportHuman != null)
        {
            this.result = transportHuman.getID();
            return this;
        }
        if (this.result != null)
        {
            Human target = (Human) this.worldInfo.getEntity(this.result);
            if (target != null)
            {
                //target死了
                if (!target.isHPDefined() || target.getHP() == 0)
                {
                    this.result = null;
                }
                //target位置有问题
                else if (!target.isPositionDefined())
                {
                    this.result = null;
                }
                else
                {
                    StandardEntity position = this.worldInfo.getPosition(target);
                    if (position != null)
                    {
                        StandardEntityURN positionURN = position.getStandardURN();
                        //如果在target在refuge或者target是at
                        if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM)
                        {
                            this.result = null;
                        }
                    }
                }
            }
        }
        if (this.result == null)
        {
            if (clustering == null)
            {
                this.result = this.calcTargetInWorld();
                return this;
            }
            this.result = this.calcTargetInCluster(clustering);
            if (this.result == null)
            {
                this.result = this.calcTargetInWorld();
            }
        }
        return this;
    }

    private EntityID calcTargetInCluster(Clustering clustering)
    {
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        //类中所有entity
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if (elements == null || elements.isEmpty())
        {
            return null;
        }

        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        //优先救agent
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            //worldInfo中的agent
            Human h = (Human) next;
            if (this.agentInfo.getID().getValue() == h.getID().getValue())
            {
                continue;
            }
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if (positionEntity != null && elements.contains(positionEntity) || elements.contains(h))
            {
                //还没死,被埋了
                if (h.isHPDefined() && h.isBuriednessDefined() && h.getHP() > 0 && h.getBuriedness() > 0)
                {
                    rescueTargets.add(h);
                }
            }
        }
        //后救市民
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN))
        {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if (positionEntity != null && positionEntity instanceof Area)
            {
                if (elements.contains(positionEntity))
                {
                    if (h.isHPDefined() && h.getHP() > 0)
                    {
                        //被埋了
                        if (h.isBuriednessDefined() && h.getBuriedness() > 0)
                        {
                            rescueTargets.add(h);
                        }
                        else
                        {
                            //没被埋,不在refuge
                            if (h.isDamageDefined() && h.getDamage() > 0 && positionEntity.getStandardURN() != REFUGE)
                            {
                                loadTargets.add(h);
                            }
                        }
                    }
                }
            }
        }
        //优先救被埋的
        if (rescueTargets.size() > 0)
        {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            //返回距离最近的
            return rescueTargets.get(0).getID();
        }
        //后救没被埋的
        if (loadTargets.size() > 0)
        {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            //返回距离最近的
            return loadTargets.get(0).getID();
        }
        return null;
    }

    private EntityID calcTargetInWorld()
    {
        List<Human> rescueTargets = new ArrayList<>();
        List<Human> loadTargets = new ArrayList<>();
        //先救agents,没被埋的话不用救
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE))
        {
            Human h = (Human) next;
            if (this.agentInfo.getID().getValue() != h.getID().getValue())
            {
                StandardEntity positionEntity = this.worldInfo.getPosition(h);
                if (positionEntity != null && h.isHPDefined() && h.isBuriednessDefined())
                {
                    if (h.getHP() > 0 && h.getBuriedness() > 0)
                    {
                        rescueTargets.add(h);
                    }
                }
            }
        }
        //后救市民
        for (StandardEntity next : this.worldInfo.getEntitiesOfType(CIVILIAN))
        {
            Human h = (Human) next;
            StandardEntity positionEntity = this.worldInfo.getPosition(h);
            if (positionEntity != null && positionEntity instanceof Area)
            {
                if (h.isHPDefined() && h.getHP() > 0)
                {
                    if (h.isBuriednessDefined() && h.getBuriedness() > 0)
                    {
                        rescueTargets.add(h);
                    }
                    else
                    {
                        if (h.isDamageDefined() && h.getDamage() > 0 && positionEntity.getStandardURN() != REFUGE)
                        {
                            loadTargets.add(h);
                        }
                    }
                }
            }
        }
        if (rescueTargets.size() > 0)
        {
            rescueTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return rescueTargets.get(0).getID();
        }
        if (loadTargets.size() > 0)
        {
            loadTargets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
            return loadTargets.get(0).getID();
        }
        return null;
    }

    @Override
    public EntityID getTarget()
    {
        return this.result;
    }

    @Override
    public HumanDetector precompute(PrecomputeData precomputeData)
    {
        super.precompute(precomputeData);
        if (this.getCountPrecompute() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public HumanDetector resume(PrecomputeData precomputeData)
    {
        super.resume(precomputeData);
        if (this.getCountResume() >= 2)
        {
            return this;
        }
        return this;
    }

    @Override
    public HumanDetector preparate()
    {
        super.preparate();
        if (this.getCountPreparate() >= 2)
        {
            return this;
        }
        return this;
    }

    //比较直线距离
    private class DistanceSorter implements Comparator<StandardEntity>
    {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference)
        {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b)
        {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}

